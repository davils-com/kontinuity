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
import com.davils.kontinuity.ratelimiter.internal.SlidingLog
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

private const val BURST: Int = 6
private const val RATE: Int = 2
private const val QUEUE: Int = 2
private const val THREE: Int = 3
private const val MANY: Int = 1_000
private const val DAYS_IN_TEN_YEARS: Int = 3_650
private const val THIRD: Long = 333_333_334L
private const val TWO_THIRDS: Long = 666_666_667L
private val HALF = 500.milliseconds
private val QUARTER = 250.milliseconds

@OptIn(ExperimentalKotest::class)
class RateLimiterAlgorithmTest : FunSpec({
    coroutineTestScope = true

    test("a fixed window lets twice the limit through across the edge of two periods") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        delay(PERIOD - QUARTER)
        limiter.tryAcquire(LIMIT) shouldBe true
        delay(QUARTER)
        limiter.tryAcquire(LIMIT) shouldBe true
    }

    test("a sliding window never lets more than the limit through within one period") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            algorithm = RateLimiterAlgorithm.SlidingWindow
        }
        delay(PERIOD - QUARTER)
        limiter.tryAcquire(LIMIT) shouldBe true
        delay(QUARTER)
        limiter.tryAcquire() shouldBe false
        limiter.reservePermission() shouldBe PERIOD - QUARTER
        limiter.availablePermissions shouldBe 0
        delay(PERIOD - QUARTER)
        limiter.availablePermissions shouldBe LIMIT - 1
    }

    test("a sliding window serves waiting callers first and frees permits one by one") {
        val timeSource = testCoroutineScheduler.timeSource
        val limiter = smallLimiter(testEnvironment(timeSource)) { algorithm = RateLimiterAlgorithm.SlidingWindow }
        limiter.tryAcquire() shouldBe true
        delay(HALF)
        limiter.tryAcquire(LIMIT - 1) shouldBe true
        val start = timeSource.markNow()
        limiter.acquire()
        start.elapsedNow() shouldBe HALF
        limiter.tryAcquire() shouldBe false
        limiter.acquire(LIMIT - 1)
        start.elapsedNow() shouldBe PERIOD
    }

    test("a sliding window gives a cancelled reservation back and drains its free permits") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            algorithm = RateLimiterAlgorithm.SlidingWindow
        }
        limiter.tryAcquire(LIMIT) shouldBe true
        val waiter = launch { limiter.acquire(LIMIT) }
        testCoroutineScheduler.runCurrent()
        limiter.availablePermissions shouldBe 0
        waiter.cancel()
        waiter.join()
        limiter.snapshot().cancelled shouldBe 1L
        delay(PERIOD)
        limiter.availablePermissions shouldBe LIMIT
        limiter.tryAcquire() shouldBe true
        limiter.drain() shouldBe LIMIT - 1
        limiter.drain() shouldBe 0
        limiter.tryAcquire() shouldBe false
    }

    test("a sliding window counts with a changed limit from the next request on") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            algorithm = RateLimiterAlgorithm.SlidingWindow
        }
        limiter.tryAcquire(LIMIT) shouldBe true
        limiter.changeLimitForPeriod(LIMIT + 1)
        limiter.availablePermissions shouldBe 1
        limiter.tryAcquire() shouldBe true
        limiter.changeLimitForPeriod(1)
        limiter.availablePermissions shouldBe 0
        limiter.reservePermission() shouldBe PERIOD
    }

    test("a sliding window bounds its log by the limit and the waiting callers") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            algorithm = RateLimiterAlgorithm.SlidingWindow
            maxWaitingCallers = QUEUE
        }
        repeat(LIMIT) { limiter.tryAcquire() shouldBe true }
        repeat(QUEUE) { limiter.reservePermission() shouldBe PERIOD }
        limiter.reservePermission() shouldBe null
        val log = limiter.gate.window as SlidingLog
        log.bookings shouldBe LIMIT + QUEUE
        shouldThrow<RequestNotPermittedException> { limiter.acquire() }
        limiter.waitingCallers shouldBe 0
    }

    test("a sliding window keeps its log small once old reservations leave") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            algorithm = RateLimiterAlgorithm.SlidingWindow
            limitForPeriod = MANY
        }
        repeat(MANY) { limiter.tryAcquire() shouldBe true }
        val log = limiter.gate.window as SlidingLog
        val grown = log.capacity
        delay(PERIOD)
        limiter.tryAcquire() shouldBe true
        log.bookings shouldBe 1
        (log.capacity < grown) shouldBe true
    }

    test("a token bucket starts full, refills continuously and holds at most its burst") {
        val timeSource = testCoroutineScheduler.timeSource
        val limiter = smallLimiter(testEnvironment(timeSource)) {
            limitForPeriod = RATE
            algorithm = tokenBucket(BURST)
        }
        limiter.availablePermissions shouldBe BURST
        repeat(BURST / RATE) { limiter.tryAcquire(RATE) shouldBe true }
        limiter.tryAcquire() shouldBe false
        val start = timeSource.markNow()
        limiter.acquire()
        start.elapsedNow() shouldBe HALF
        delay(PERIOD * BURST)
        limiter.availablePermissions shouldBe BURST
    }

    test("a token bucket gives a cancelled reservation back unless it was drained") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            limitForPeriod = RATE
            algorithm = tokenBucket(RATE)
        }
        limiter.tryAcquire(RATE) shouldBe true
        val refunded = launch { limiter.acquire(RATE) }
        testCoroutineScheduler.runCurrent()
        refunded.cancel()
        refunded.join()
        limiter.reservePermission(RATE) shouldBe PERIOD
        val kept = launch { limiter.acquire(RATE) }
        testCoroutineScheduler.runCurrent()
        limiter.drain() shouldBe 0
        kept.cancel()
        kept.join()
        limiter.reservePermission(RATE) shouldBe PERIOD * THREE
    }

    test("a token bucket drains its free permits and takes a new limit") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            limitForPeriod = RATE
            algorithm = tokenBucket(BURST)
        }
        limiter.tryAcquire() shouldBe true
        limiter.drain() shouldBe BURST - 1
        limiter.availablePermissions shouldBe 0
        limiter.changeLimitForPeriod(BURST)
        delay(PERIOD)
        limiter.availablePermissions shouldBe BURST
        shouldThrow<IllegalArgumentException> { limiter.changeLimitForPeriod(BURST + 1) }
    }

    test("a token bucket keeps fractions of a nanosecond when the period does not divide") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            limitForPeriod = LIMIT
            algorithm = tokenBucket(LIMIT)
        }
        limiter.tryAcquire(LIMIT) shouldBe true
        limiter.reservePermission() shouldBe THIRD.nanoseconds
        limiter.reservePermission() shouldBe TWO_THIRDS.nanoseconds
        limiter.reservePermission() shouldBe PERIOD
    }

    test("a token bucket that refills in ten years still counts exactly") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            limitForPeriod = 1
            limitRefreshPeriod = RateLimiterConfig.HARD_REFRESH_PERIOD
            algorithm = tokenBucket(DAYS_IN_TEN_YEARS)
        }
        limiter.availablePermissions shouldBe DAYS_IN_TEN_YEARS
        limiter.drain() shouldBe DAYS_IN_TEN_YEARS
        limiter.availablePermissions shouldBe 0
        limiter.reservePermission() shouldBe null
        delay(1.days)
        limiter.availablePermissions shouldBe 1
        shouldThrow<IllegalArgumentException> { limiter.changeLimitForPeriod(DAYS_IN_TEN_YEARS + 1) }
    }

    test("a token bucket takes a lower limit only while it refills within ten years") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            limitForPeriod = RATE
            limitRefreshPeriod = RateLimiterConfig.HARD_REFRESH_PERIOD
            algorithm = tokenBucket(DAYS_IN_TEN_YEARS + 1)
        }
        shouldThrow<IllegalArgumentException> { limiter.changeLimitForPeriod(1) }
        limiter.limitForPeriod shouldBe RATE
    }
})
