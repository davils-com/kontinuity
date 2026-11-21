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

package com.davils.kontinuity.circuitbreaker

import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.validateOrThrow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The window of recent calls whose outcomes decide whether a circuit breaker opens.
 *
 * @since 1.0.0
 */
public sealed interface SlidingWindow : Validatable {
    /**
     * The outcomes of the last [size] calls.
     *
     * @property size how many calls the window holds, from 1 to [HARD_SIZE].
     * @since 1.0.0
     */
    public class CountBased internal constructor(public val size: Int) : SlidingWindow {
        init {
            validateOrThrow(SUBJECT)
        }

        /**
         * Checks that [size] is from 1 to [HARD_SIZE].
         *
         * @receiver the validation that collects the violations.
         * @since 1.0.0
         */
        override fun ValidationScope.validate() {
            inRange(property = ::size, value = size, range = 1..HARD_SIZE)
        }

        /**
         * Tells whether [other] is a count-based window of the same size.
         *
         * @param other the object to compare with.
         * @return true if [other] is equal.
         * @since 1.0.0
         */
        override fun equals(other: Any?): Boolean = other is CountBased && other.size == size

        /**
         * Returns a hash of the size.
         *
         * @return the hash.
         * @since 1.0.0
         */
        override fun hashCode(): Int = size

        /**
         * Shows the size.
         *
         * @return the window as text.
         * @since 1.0.0
         */
        override fun toString(): String = "CountBased($size)"
    }

    /**
     * The outcomes of the calls of the last [length], kept in [buckets] equal parts; a part leaves the window as a
     * whole when it is older than [length].
     *
     * @property length how far back the window reaches, positive and at most [HARD_LENGTH].
     * @property buckets how many parts the window keeps, from 1 to [HARD_BUCKETS].
     * @since 1.0.0
     */
    public class TimeBased internal constructor(public val length: Duration, public val buckets: Int) : SlidingWindow {
        init {
            validateOrThrow(SUBJECT)
        }

        /**
         * Checks that [length] and [buckets] are within their bounds.
         *
         * @receiver the validation that collects the violations.
         * @since 1.0.0
         */
        override fun ValidationScope.validate() {
            val isPositive = length.isPositive()
            if (!isPositive) fail(property = ::length, message = "must be positive")
            inRange(property = ::length, value = length, range = Duration.ZERO..HARD_LENGTH)
            inRange(property = ::buckets, value = buckets, range = 1..HARD_BUCKETS)
        }

        /**
         * Tells whether [other] is a time-based window of the same length and buckets.
         *
         * @param other the object to compare with.
         * @return true if [other] is equal.
         * @since 1.0.0
         */
        override fun equals(other: Any?): Boolean =
            other is TimeBased && other.length == length && other.buckets == buckets

        /**
         * Returns a hash of the length and the buckets.
         *
         * @return the hash.
         * @since 1.0.0
         */
        override fun hashCode(): Int = length.hashCode() * HASH_FACTOR + buckets

        /**
         * Shows the length and the buckets.
         *
         * @return the window as text.
         * @since 1.0.0
         */
        override fun toString(): String = "TimeBased($length, buckets=$buckets)"
    }

    /**
     * The hard ceilings of a window.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The most calls of a count-based window: 100,000.
         *
         * @since 1.0.0
         */
        public const val HARD_SIZE: Int = 100_000

        /**
         * The longest time-based window: one day.
         *
         * @since 1.0.0
         */
        public val HARD_LENGTH: Duration = 1.days

        /**
         * The most buckets of a time-based window: 3,600.
         *
         * @since 1.0.0
         */
        public const val HARD_BUCKETS: Int = 3_600

        private const val SUBJECT: String = "sliding window"
        private const val HASH_FACTOR: Int = 31
    }
}
