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

package com.davils.kontinuity.timelimiter

import com.davils.kollect.core.meterRegistry
import com.davils.konfetti.testing.EventRecorder
import com.davils.konfetti.testing.testEventBus
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import com.davils.kontinuity.timelimiter.fixture.LONG
import com.davils.kontinuity.timelimiter.fixture.SHORT
import com.davils.kontinuity.timelimiter.fixture.TIMEOUT
import com.davils.kontinuity.timelimiter.fixture.gauge
import com.davils.kontinuity.timelimiter.fixture.hardLimiter
import com.davils.kontinuity.timelimiter.fixture.has
import com.davils.kontinuity.timelimiter.fixture.quick
import com.davils.kontinuity.timelimiter.fixture.quietly
import com.davils.kontinuity.timelimiter.fixture.slow
import com.davils.kontinuity.timelimiter.fixture.softLimiter
import com.davils.kontinuity.timelimiter.fixture.sum
import com.davils.kontinuity.timelimiter.fixture.withScope
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NAME: String = "payments"
private const val CALLS: String = "kontinuity.time_limiter.calls"
private const val DETACHED: String = "kontinuity.time_limiter.detached"
private const val BROKEN: String = "broken call"

@OptIn(ExperimentalKotest::class)
class TimeLimiterTelemetryTest : FunSpec({
    coroutineTestScope = true

    test("the events tell every outcome") {
        withScope { scope ->
            val limiter = softLimiter(
                testEnvironment(testCoroutineScheduler.timeSource, scope),
                isCancellingOnTimeout = false,
                maxDetached = 1,
            )
            val log = recordEvents(limiter.events)
            limiter.quick()
            quietly { limiter.execute { throw IllegalArgumentException(BROKEN) } }
            quietly {
                limiter.execute {
                    delay(LONG)
                    throw object : RuntimeException(BROKEN) {}
                }
            }
            quietly { limiter.quick() }
            delay(LONG)
            log.stop()
            log.events.map(TimeLimiterEvent::toString) shouldContainExactly listOf(
                "Succeeded(payments, 500ms)",
                "Failed(payments, 0s, IllegalArgumentException)",
                "TimedOut(payments, 1s)",
                "DetachedRejected(payments, maxDetached=1)",
                "DetachedFailed(payments, Throwable)",
            )
            log.events.map(TimeLimiterEvent::kind).distinct() shouldBe listOf(ResilienceKind.TimeLimiter)
            log.only<TimeLimiterEvent.Succeeded>().single().duration shouldBe SHORT
            log.only<TimeLimiterEvent.Failed>().single().failureType shouldBe "IllegalArgumentException"
            log.only<TimeLimiterEvent.TimedOut>().single().timeout shouldBe TIMEOUT
            log.only<TimeLimiterEvent.DetachedRejected>().single().maxDetached shouldBe 1
        }
    }

    test("the meters count calls and detached work") {
        withScope { scope ->
            val registry = meterRegistry()
            val environment = testEnvironment(testCoroutineScheduler.timeSource, scope) { meters(registry) }
            val limiter = softLimiter(environment, isCancellingOnTimeout = false, maxDetached = 1)
            registry.gauge(DETACHED, NAME) shouldBe 0.0
            limiter.quick()
            quietly { limiter.execute { throw IllegalArgumentException(BROKEN) } }
            quietly { limiter.slow() }
            registry.gauge(DETACHED, NAME) shouldBe 1.0
            quietly { limiter.quick() }
            delay(LONG)
            registry.gauge(DETACHED, NAME) shouldBe 0.0
            val caller = launch { limiter.slow() }
            delay(SHORT)
            caller.cancel()
            caller.join()
            delay(LONG)
            registry.sum(CALLS, NAME, "successful") shouldBe 1.0
            registry.sum(CALLS, NAME, "failed") shouldBe 1.0
            registry.sum(CALLS, NAME, "timed_out") shouldBe 1.0
            registry.sum(CALLS, NAME, "rejected") shouldBe 1.0
            registry.sum(CALLS, NAME, "cancelled") shouldBe 1.0
            registry.gauge(DETACHED, NAME) shouldBe 0.0
            registry.has("kontinuity.time_limiter.call.duration") shouldBe true
        }
    }

    test("the events reach the topic of the event bus") {
        withScope { scope ->
            val bus = testEventBus(scope)
            val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource) { events(bus) })
            val recorder = EventRecorder<TimeLimiterEvent>()
            bus[TimeLimiterEvent.TOPIC].consume("recorder") { handle(recorder::record) }
            limiter.quick()
            recorder.awaitCount(1)
            recorder.payloads.single().kind shouldBe ResilienceKind.TimeLimiter
        }
    }

    test("a limiter and its snapshot show their values") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        limiter.toString() shouldBe "TimeLimiter(payments, 1s, Hard)"
        limiter.snapshot().toString() shouldBe "TimeLimiterSnapshot(successful=0, failed=0, timedOut=0, " +
            "cancelled=0, rejected=0, detachedInFlight=0, detachedFailed=0)"
    }
})
