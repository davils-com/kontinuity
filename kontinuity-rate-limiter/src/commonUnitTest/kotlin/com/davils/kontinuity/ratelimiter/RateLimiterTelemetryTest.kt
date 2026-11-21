/*
 * Copyright 2026 Davils
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.davils.kontinuity.ratelimiter

import com.davils.kollect.core.meterRegistry
import com.davils.konfetti.testing.EventRecorder
import com.davils.konfetti.testing.testEventBus
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.ratelimiter.fixture.LIMIT
import com.davils.kontinuity.ratelimiter.fixture.gauge
import com.davils.kontinuity.ratelimiter.fixture.quietly
import com.davils.kontinuity.ratelimiter.fixture.recorded
import com.davils.kontinuity.ratelimiter.fixture.smallLimiter
import com.davils.kontinuity.ratelimiter.fixture.sum
import com.davils.kontinuity.ratelimiter.fixture.withScope
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val NAME: String = "search"
private const val CALLS: String = "kontinuity.rate_limiter.calls"
private const val WIDER: Int = 4
private const val THREE: Int = 3

@OptIn(ExperimentalKotest::class)
class RateLimiterTelemetryTest : FunSpec({
    coroutineTestScope = true

    test("the events tell every outcome, drain and change") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        val log = recordEvents(limiter.events)
        limiter.acquire(2)
        limiter.tryAcquire(2)
        limiter.acquire()
        val waiter = launch { limiter.acquire(LIMIT) }
        testCoroutineScheduler.runCurrent()
        waiter.cancelAndJoin()
        limiter.drain()
        limiter.changeLimitForPeriod(WIDER)
        limiter.changeTimeout(1.seconds)
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.events.map(RateLimiterEvent::toString) shouldContainExactly listOf(
            "Acquired(search, permits=2, waited=0s)",
            "Rejected(search, permits=2, availableIn=1s)",
            "Acquired(search, permits=1, waited=0s)",
            "Cancelled(search, permits=3, refunded=true)",
            "Drained(search, permits=0)",
            "LimitChanged(search, limitForPeriod=4, timeout=5s)",
            "LimitChanged(search, limitForPeriod=4, timeout=1s)",
        )
        log.events.all { event -> event.kind == ResilienceKind.RateLimiter } shouldBe true
    }

    test("an acquired event tells how long the caller waited") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        val log = recordEvents(limiter.events)
        limiter.acquire(LIMIT)
        limiter.acquire()
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.only<RateLimiterEvent.Acquired>().last().waited shouldBe 1.seconds
    }

    test("the meters count requests, waits and the levels of the limiter") {
        val registry = meterRegistry()
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource) { meters(registry) })
        limiter.acquire()
        registry.gauge("kontinuity.rate_limiter.available_permissions", NAME) shouldBe (LIMIT - 1).toDouble()
        limiter.acquire(LIMIT - 1)
        limiter.tryAcquire()
        val waiter = launch { limiter.acquire() }
        testCoroutineScheduler.runCurrent()
        registry.gauge("kontinuity.rate_limiter.waiting_callers", NAME) shouldBe 1.0
        waiter.cancelAndJoin()
        limiter.acquire()
        registry.sum(CALLS, NAME, "acquired") shouldBe THREE.toDouble()
        registry.sum(CALLS, NAME, "rejected") shouldBe 1.0
        registry.sum(CALLS, NAME, "cancelled") shouldBe 1.0
        registry.recorded("kontinuity.rate_limiter.wait", NAME) shouldBe THREE.toLong()
        registry.gauge("kontinuity.rate_limiter.waiting_callers", NAME) shouldBe 0.0
        limiter.drain()
        limiter.changeLimitForPeriod(WIDER)
        registry.gauge("kontinuity.rate_limiter.available_permissions", NAME) shouldBe 0.0
    }

    test("the events reach the topic of the event bus") {
        withScope { scope ->
            val bus = testEventBus(scope)
            val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource) { events(bus) })
            val recorder = EventRecorder<RateLimiterEvent>()
            bus[RateLimiterEvent.TOPIC].consume("recorder") { handle(recorder::record) }
            limiter.acquire()
            recorder.awaitCount(1)
            recorder.payloads.single().kind shouldBe ResilienceKind.RateLimiter
        }
    }

    test("every request is counted exactly once") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) { maxWaitingCallers = 2 }
        val requests = LIMIT * WIDER
        val callers = List(requests) { index ->
            launch {
                quietly {
                    if (index % WIDER == 0) limiter.tryAcquire()
                    if (index % WIDER != 0) limiter.acquire()
                }
            }
        }
        testCoroutineScheduler.runCurrent()
        callers[WIDER + 1].cancelAndJoin()
        callers.forEach { caller -> caller.join() }
        limiter.reservePermission()
        val snapshot = limiter.snapshot()
        snapshot.acquired + snapshot.rejected + snapshot.cancelled shouldBe (requests + 1).toLong()
        snapshot.waitingCallers shouldBe 0
        snapshot.cancelled shouldBe 1L
    }

    test("a limiter shows its name, its algorithm and its limit") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        limiter.toString() shouldBe "RateLimiter(search, FixedWindow, 3 per 1s)"
        limiter.kind shouldBe ResilienceKind.RateLimiter
        limiter.snapshot().toString() shouldBe "RateLimiterSnapshot(availablePermissions=3, waitingCallers=0, " +
            "limitForPeriod=3, timeout=5s, acquired=0, rejected=0, cancelled=0, permitsGranted=0)"
    }
})
