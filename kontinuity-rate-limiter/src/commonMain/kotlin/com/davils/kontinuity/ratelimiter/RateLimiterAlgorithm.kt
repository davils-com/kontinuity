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

import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.validateOrThrow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * How a [RateLimiter] counts its permits.
 *
 * Every algorithm hands out at most [RateLimiterConfig.limitForPeriod] permits per
 * [RateLimiterConfig.limitRefreshPeriod] on average; they differ in how they treat the edges of a period:
 *
 * - [FixedWindow] splits the time into cycles of one period and refills all permits at the start of each cycle, the
 *   way resilience4j's `AtomicRateLimiter` does. It is lock-free and the cheapest, but a burst at the end of one cycle
 *   and another at the start of the next can let up to twice the limit through within one period.
 * - [SlidingWindow] keeps a log of the reservations and never lets more than the limit through within any span of
 *   one period, at the cost of a lock and of memory for every reservation of the last period.
 * - [TokenBucket] refills the permits continuously, one every `limitRefreshPeriod / limitForPeriod`, into a bucket of
 *   [TokenBucket.burst] permits; it smooths the rate and allows bursts up to the size of the bucket. It is lock-free.
 *
 * Callers that must wait are served in the order of their reservations by every algorithm.
 *
 * @since 1.0.0
 */
public sealed interface RateLimiterAlgorithm {
    /**
     * Fixed cycles of one period that refill all permits at their start; the default.
     *
     * A caller that finds too few permits in the current cycle reserves all of them in a later cycle and waits for
     * its start, so the permits of future cycles are handed out in the order of the reservations. Unlike
     * resilience4j's `AtomicRateLimiter`, a reservation never borrows the rest of the current cycle: that rest stays
     * unused, so no cycle ever starts more than the limit of calls, waiting callers included.
     *
     * @since 1.0.0
     */
    public object FixedWindow : RateLimiterAlgorithm {
        /**
         * Shows the name of the algorithm.
         *
         * @return `FixedWindow`.
         * @since 1.0.0
         */
        override fun toString(): String = "FixedWindow"
    }

    /**
     * A sliding log of the reservations: within any span of one period, at most the limit of permits are handed out.
     *
     * The log keeps one entry per reservation of the last period and one per waiting caller, at most
     * `limitForPeriod + maxWaitingCallers` entries; finding the time at which enough permits are free takes
     * logarithmic time in the number of entries.
     *
     * @since 1.0.0
     */
    public object SlidingWindow : RateLimiterAlgorithm {
        /**
         * Shows the name of the algorithm.
         *
         * @return `SlidingWindow`.
         * @since 1.0.0
         */
        override fun toString(): String = "SlidingWindow"
    }

    /**
     * A bucket of [burst] permits that refills continuously at `limitForPeriod` permits per period and starts full.
     *
     * After a quiet time a caller can take up to [burst] permits at once; under steady load the rate is the limit
     * per period. [burst] must be at least [RateLimiterConfig.limitForPeriod], and an empty bucket must refill within
     * [HARD_REFILL]: `burst × limitRefreshPeriod / limitForPeriod` is at most ten years, which keeps the arithmetic of
     * the bucket exact.
     *
     * @property burst how many permits the bucket holds, from 1 to [HARD_BURST].
     * @since 1.0.0
     */
    public class TokenBucket internal constructor(public val burst: Int) : RateLimiterAlgorithm, Validatable {
        init {
            validateOrThrow(SUBJECT)
        }

        /**
         * Checks that [burst] is from 1 to [HARD_BURST].
         *
         * @receiver the validation that collects the violations.
         * @since 1.0.0
         */
        override fun ValidationScope.validate() {
            inRange(property = ::burst, value = burst, range = 1..HARD_BURST)
        }

        internal fun fits(limit: Int, period: Duration): Boolean {
            val refill = burst.toDouble() * period.inWholeNanoseconds / limit
            return burst >= limit && refill <= HARD_REFILL.inWholeNanoseconds.toDouble()
        }

        /**
         * Tells whether [other] is a token bucket of the same size.
         *
         * @param other the object to compare with.
         * @return true if [other] is equal.
         * @since 1.0.0
         */
        override fun equals(other: Any?): Boolean = other is TokenBucket && other.burst == burst

        /**
         * Returns a hash of the size.
         *
         * @return the hash.
         * @since 1.0.0
         */
        override fun hashCode(): Int = burst

        /**
         * Shows the size of the bucket.
         *
         * @return the algorithm as text.
         * @since 1.0.0
         */
        override fun toString(): String = "TokenBucket(burst=$burst)"
    }

    /**
     * The hard ceilings of a token bucket.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The most permits a token bucket holds: 1,000,000.
         *
         * @since 1.0.0
         */
        public const val HARD_BURST: Int = 1_000_000

        /**
         * The longest time an empty token bucket may take to refill: ten years, 3,650 days.
         *
         * @since 1.0.0
         */
        public val HARD_REFILL: Duration = 3_650.days

        private const val SUBJECT: String = "token bucket"
    }
}
