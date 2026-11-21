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

import com.davils.kontinuity.ratelimiter.fixture.ACTIONS
import com.davils.kontinuity.ratelimiter.fixture.CANCEL
import com.davils.kontinuity.ratelimiter.fixture.MAX_WAIT
import com.davils.kontinuity.ratelimiter.fixture.ScanningLog
import com.davils.kontinuity.ratelimiter.fixture.checkBucket
import com.davils.kontinuity.ratelimiter.fixture.heaviestCycle
import com.davils.kontinuity.ratelimiter.fixture.heaviestSpan
import com.davils.kontinuity.ratelimiter.fixture.run
import com.davils.kontinuity.ratelimiter.internal.FixedWindow
import com.davils.kontinuity.ratelimiter.internal.PermitWindow
import com.davils.kontinuity.ratelimiter.internal.Reservation
import com.davils.kontinuity.ratelimiter.internal.SlidingLog
import com.davils.kontinuity.ratelimiter.internal.TokenBucket
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll

private const val ITERATIONS: Int = 300
private const val STEPS: Int = 60
private const val MAX_LIMIT: Int = 12
private const val MAX_PERIOD: Long = 2_000L
private const val UNBOUNDED: Long = Long.MAX_VALUE / 8L
private const val DRAIN: Int = 9
private const val QUEUE: Int = 4

private val limits: Arb<Int> = Arb.int(1..MAX_LIMIT)
private val periods: Arb<Long> = Arb.long(1L..MAX_PERIOD)
private val gaps: Arb<List<Long>> = Arb.list(Arb.long(0L..MAX_PERIOD), STEPS..STEPS)
private val picks: Arb<List<Int>> = Arb.list(Arb.int(0 until ACTIONS * MAX_LIMIT), STEPS..STEPS)

private fun windowsOf(limit: Int, periodNanos: Long): List<PermitWindow> = listOf(
    FixedWindow(periodNanos, limit),
    SlidingLog(periodNanos, Int.MAX_VALUE / 2),
    TokenBucket(periodNanos = periodNanos, burst = limit, initialLimit = limit),
)

class PermitWindowPropertyTest : FunSpec({
    test("the sliding window answers exactly like a scanning reference") {
        checkAll(
            iterations = ITERATIONS,
            genA = limits,
            genB = periods,
            genC = gaps,
            genD = picks,
        ) { limit, period, steps, choices ->
            val sliding = SlidingLog(period, QUEUE)
            val reference = ScanningLog(period, QUEUE)
            val live = mutableListOf<Pair<Reservation, Reservation>>()
            var now = 0L
            steps.zip(choices).forEach { (gap, pick) ->
                now += gap
                val permits = pick % limit + 1
                val wait = pick * gap % MAX_WAIT
                val actual = sliding.reserve(permits = permits, nowNanos = now, maxWaitNanos = wait, limit = limit)
                val expected = reference.reserve(permits = permits, nowNanos = now, maxWaitNanos = wait, limit = limit)
                actual.isGranted shouldBe expected.isGranted
                actual.waitNanos shouldBe expected.waitNanos
                if (actual.isGranted) live += actual to expected
                val action = pick % ACTIONS
                val cancelled = live.getOrNull(pick % (live.size + 1)).takeIf { action == CANCEL }
                if (cancelled != null) cancelled.first.refund(now) shouldBe cancelled.second.refund(now)
                if (action == DRAIN) sliding.drain(now, limit) shouldBe reference.drain(now, limit)
                sliding.available(now, limit) shouldBe reference.available(now, limit)
            }
        }
    }

    test("the sliding window never hands out more than the limit within any span of one period") {
        checkAll(
            iterations = ITERATIONS,
            genA = limits,
            genB = periods,
            genC = gaps,
            genD = picks,
        ) { limit, period, steps, choices ->
            val granted = run(
                window = SlidingLog(period, Int.MAX_VALUE / 2),
                limit = limit,
                gaps = steps,
                picks = choices,
            )
            heaviestSpan(granted, period) shouldBeLessThanOrEqual limit.toLong()
        }
    }

    test("the fixed window never hands out more than the limit within one cycle") {
        checkAll(
            iterations = ITERATIONS,
            genA = limits,
            genB = periods,
            genC = gaps,
            genD = picks,
        ) { limit, period, steps, choices ->
            val granted = run(window = FixedWindow(period, limit), limit = limit, gaps = steps, picks = choices)
            heaviestCycle(granted, period) shouldBeLessThanOrEqual limit.toLong()
        }
    }

    test("the token bucket never hands out more than its burst and its rate allow") {
        checkAll(
            iterations = ITERATIONS,
            genA = limits,
            genB = periods,
            genC = gaps,
            genD = picks,
        ) { limit, period, steps, choices ->
            val burst = limit + choices.first() % limit
            val bucket = TokenBucket(periodNanos = period, burst = burst, initialLimit = limit)
            val granted = run(window = bucket, limit = limit, gaps = steps, picks = choices)
            checkBucket(granted = granted, burst = burst, limit = limit, periodNanos = period)
        }
    }

    test("every window reports as free exactly the permits a caller could take at once") {
        checkAll(iterations = ITERATIONS, genA = limits, genB = periods, genC = gaps) { limit, period, steps ->
            windowsOf(limit, period).forEach { window ->
                var now = 0L
                steps.forEach { gap ->
                    now += gap
                    val free = window.available(now, limit)
                    if (free < limit) {
                        val more = window.reserve(permits = free + 1, nowNanos = now, maxWaitNanos = 0L, limit = limit)
                        more.isGranted shouldBe false
                    }
                    if (free > 0) {
                        val taken = window.reserve(permits = free, nowNanos = now, maxWaitNanos = 0L, limit = limit)
                        taken.waitNanos shouldBe 0L
                    }
                }
            }
        }
    }

    test("every window gives a reservation back that is not due yet") {
        checkAll(iterations = ITERATIONS, genA = limits, genB = periods) { limit, period ->
            windowsOf(limit, period).forEach { window ->
                window.reserve(permits = limit, nowNanos = 0L, maxWaitNanos = 0L, limit = limit).isGranted shouldBe true
                val queued = window.reserve(permits = limit, nowNanos = 0L, maxWaitNanos = UNBOUNDED, limit = limit)
                queued.waitNanos shouldBeGreaterThanOrEqual 1L
                queued.refund(0L) shouldBe true
                val again = window.reserve(permits = limit, nowNanos = 0L, maxWaitNanos = UNBOUNDED, limit = limit)
                again.waitNanos shouldBe queued.waitNanos
                again.refund(again.waitNanos) shouldBe false
            }
        }
    }

    test("a refused reservation changes nothing and gives nothing back") {
        checkAll(iterations = ITERATIONS, genA = limits, genB = periods) { limit, period ->
            windowsOf(limit, period).forEach { window ->
                window.reserve(permits = limit, nowNanos = 0L, maxWaitNanos = 0L, limit = limit)
                val refused = window.reserve(permits = 1, nowNanos = 0L, maxWaitNanos = 0L, limit = limit)
                refused.isGranted shouldBe false
                refused.refund(0L) shouldBe false
                window.available(0L, limit) shouldBe 0
            }
        }
    }
})
