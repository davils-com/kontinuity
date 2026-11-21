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

import com.davils.kontinuity.ratelimiter.fixture.LIMIT
import com.davils.kontinuity.ratelimiter.fixture.PERIOD
import com.davils.kontinuity.ratelimiter.fixture.smallLimiter
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

private const val WIDER: Int = 5
private const val THREE: Int = 3
private val HALF = 500.milliseconds

@OptIn(ExperimentalKotest::class)
class RateLimiterTest : FunSpec({
    coroutineTestScope = true

    test("a fixed window hands out the limit per period and refills at the start of the next") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        limiter.availablePermissions shouldBe LIMIT
        repeat(LIMIT) { limiter.tryAcquire() shouldBe true }
        limiter.tryAcquire() shouldBe false
        limiter.availablePermissions shouldBe 0
        delay(PERIOD)
        limiter.availablePermissions shouldBe LIMIT
        limiter.tryAcquire(LIMIT) shouldBe true
    }

    test("a caller waits for the permits of a later period in the order of the requests") {
        val timeSource = testCoroutineScheduler.timeSource
        val limiter = smallLimiter(testEnvironment(timeSource))
        val start = timeSource.markNow()
        limiter.acquire(LIMIT)
        limiter.acquire(LIMIT)
        start.elapsedNow() shouldBe PERIOD
        limiter.acquire(1)
        limiter.acquire(LIMIT)
        start.elapsedNow() shouldBe PERIOD * THREE
        limiter.snapshot().permitsGranted shouldBe (LIMIT * THREE + 1).toLong()
    }

    test("a caller whose permits are due after its timeout is refused at once") {
        val timeSource = testCoroutineScheduler.timeSource
        val limiter = smallLimiter(testEnvironment(timeSource))
        limiter.acquire(LIMIT)
        val start = timeSource.markNow()
        val refusal = shouldThrow<RequestNotPermittedException> { limiter.acquire(permits = 1, timeout = HALF) }
        refusal.permits shouldBe 1
        refusal.availableIn shouldBe PERIOD
        refusal.message shouldBe "The rate limiter search permits no 1 permits now; they are free in 1s"
        start.elapsedNow() shouldBe Duration.ZERO
        limiter.acquire(permits = 1, timeout = PERIOD)
        start.elapsedNow() shouldBe PERIOD
    }

    test("tryAcquire never waits") {
        val timeSource = testCoroutineScheduler.timeSource
        val limiter = smallLimiter(testEnvironment(timeSource))
        val start = timeSource.markNow()
        limiter.tryAcquire(LIMIT) shouldBe true
        limiter.tryAcquire() shouldBe false
        start.elapsedNow() shouldBe Duration.ZERO
        limiter.snapshot().rejected shouldBe 1L
    }

    test("a reservation returns the wait the caller does itself") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        limiter.reservePermission(LIMIT) shouldBe Duration.ZERO
        limiter.reservePermission() shouldBe PERIOD
        limiter.waitingCallers shouldBe 0
        limiter.changeTimeout(HALF)
        limiter.reservePermission() shouldBe null
        limiter.snapshot().acquired shouldBe 2L
        limiter.snapshot().rejected shouldBe 1L
    }

    test("a reservation is refused while the queue of waiting callers is full") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) { maxWaitingCallers = 0 }
        limiter.reservePermission(LIMIT) shouldBe Duration.ZERO
        limiter.reservePermission() shouldBe null
    }

    test("a caller that would wait while the queue is full is refused") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) { maxWaitingCallers = 1 }
        limiter.acquire(LIMIT)
        val waiter = async { limiter.acquire(LIMIT) }
        testCoroutineScheduler.runCurrent()
        limiter.waitingCallers shouldBe 1
        val refusal = shouldThrow<RequestNotPermittedException> { limiter.acquire() }
        refusal.availableIn shouldBe PERIOD * 2
        waiter.await()
        limiter.waitingCallers shouldBe 0
        limiter.acquire()
        limiter.snapshot().acquired shouldBe THREE.toLong()
    }

    test("with no room for waiting callers nobody waits") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) { maxWaitingCallers = 0 }
        limiter.acquire(LIMIT)
        shouldThrow<RequestNotPermittedException> { limiter.acquire() }
        limiter.waitingCallers shouldBe 0
    }

    test("with a timeout of zero nobody waits") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) { timeout = Duration.ZERO }
        limiter.acquire(LIMIT)
        shouldThrow<RequestNotPermittedException> { limiter.acquire() }
    }

    test("a caller cancelled while it waits gives its reservation back") {
        val timeSource = testCoroutineScheduler.timeSource
        val limiter = smallLimiter(testEnvironment(timeSource))
        val log = recordEvents(limiter.events)
        limiter.acquire(LIMIT)
        val waiter = launch { limiter.acquire(LIMIT) }
        testCoroutineScheduler.runCurrent()
        limiter.waitingCallers shouldBe 1
        waiter.cancelAndJoin()
        limiter.waitingCallers shouldBe 0
        val start = timeSource.markNow()
        limiter.acquire(LIMIT)
        start.elapsedNow() shouldBe PERIOD
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.only<RateLimiterEvent.Cancelled>().single().isRefunded shouldBe true
        val snapshot = limiter.snapshot()
        snapshot.cancelled shouldBe 1L
        snapshot.acquired shouldBe 2L
        snapshot.permitsGranted shouldBe (LIMIT * 2).toLong()
    }

    test("drain takes the free permits of the current period") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        limiter.tryAcquire() shouldBe true
        limiter.drain() shouldBe LIMIT - 1
        limiter.drain() shouldBe 0
        limiter.tryAcquire() shouldBe false
        delay(PERIOD)
        limiter.availablePermissions shouldBe LIMIT
    }

    test("drain takes nothing while callers wait for a later period") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        limiter.tryAcquire(LIMIT - 1) shouldBe true
        limiter.reservePermission(LIMIT) shouldBe PERIOD
        limiter.drain() shouldBe 0
        limiter.availablePermissions shouldBe 0
    }

    test("a changed limit applies from the next period on") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        limiter.tryAcquire(LIMIT) shouldBe true
        limiter.changeLimitForPeriod(WIDER)
        limiter.limitForPeriod shouldBe WIDER
        limiter.tryAcquire() shouldBe false
        delay(PERIOD)
        limiter.tryAcquire(WIDER) shouldBe true
        limiter.changeLimitForPeriod(1)
        delay(PERIOD)
        limiter.availablePermissions shouldBe 1
        shouldThrow<IllegalArgumentException> { limiter.tryAcquire(2) }
    }

    test("a changed limit and timeout are checked against their ceilings") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        limiter.changeLimitForPeriod(RateLimiterConfig.HARD_LIMIT_FOR_PERIOD)
        shouldThrow<IllegalArgumentException> {
            limiter.changeLimitForPeriod(RateLimiterConfig.HARD_LIMIT_FOR_PERIOD + 1)
        }
        shouldThrow<IllegalArgumentException> { limiter.changeLimitForPeriod(0) }
        limiter.changeTimeout(RateLimiterConfig.HARD_TIMEOUT)
        limiter.timeout shouldBe 1.hours
        shouldThrow<IllegalArgumentException> { limiter.changeTimeout(RateLimiterConfig.HARD_TIMEOUT + 1.nanoseconds) }
        shouldThrow<IllegalArgumentException> { limiter.changeTimeout(-1.nanoseconds) }
        limiter.changeTimeout(Duration.ZERO)
        limiter.timeout shouldBe Duration.ZERO
    }

    test("the permits and the timeout of a call are checked against their bounds") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        shouldThrow<IllegalArgumentException> { limiter.acquire(0) }
        shouldThrow<IllegalArgumentException> { limiter.acquire(LIMIT + 1) }
        shouldThrow<IllegalArgumentException> { limiter.tryAcquire(LIMIT + 1) }
        shouldThrow<IllegalArgumentException> { limiter.reservePermission(0) }
        shouldThrow<IllegalArgumentException> { limiter.execute(LIMIT + 1) { } }
        shouldThrow<IllegalArgumentException> {
            limiter.acquire(permits = 1, timeout = RateLimiterConfig.HARD_TIMEOUT + 1.nanoseconds)
        }
        shouldThrow<IllegalArgumentException> { limiter.acquire(permits = 1, timeout = -1.nanoseconds) }
        limiter.acquire(permits = LIMIT, timeout = RateLimiterConfig.HARD_TIMEOUT)
        val snapshot = limiter.snapshot()
        snapshot.acquired + snapshot.rejected + snapshot.cancelled shouldBe 1L
    }

    test("the largest limit hands out a million permits in one call") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            limitForPeriod = RateLimiterConfig.HARD_LIMIT_FOR_PERIOD
        }
        limiter.tryAcquire(RateLimiterConfig.HARD_LIMIT_FOR_PERIOD) shouldBe true
        limiter.reservePermission(RateLimiterConfig.HARD_LIMIT_FOR_PERIOD) shouldBe PERIOD
    }
})
