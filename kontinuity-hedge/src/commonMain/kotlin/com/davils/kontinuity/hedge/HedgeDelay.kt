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

package com.davils.kontinuity.hedge

import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.positive
import com.davils.kore.core.validation.validateOrThrow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long a [Hedge] waits for a copy of a call before it starts the next copy: `fixedDelay(50.milliseconds)` or
 * `percentileDelay(percentile = 0.99)`.
 *
 * A delay is an immutable value, validated when it is created and safe to share between hedges and threads. Every
 * delay a hedge waits is positive and at most [HARD_DELAY]; a recorded failure of a copy starts the next copy before
 * its delay is over.
 *
 * @since 1.0.0
 */
public sealed interface HedgeDelay : Validatable {
    /**
     * The delay a hedge waits before it has observed any latency: the fixed delay itself, or the fallback of a
     * percentile delay.
     *
     * @since 1.0.0
     */
    public val initial: Duration

    /**
     * The same delay before every copy, as the fixed hedge duration of resilience4j.
     *
     * @property duration the delay, positive and at most [HARD_DELAY].
     * @since 1.0.0
     */
    public class Fixed internal constructor(public val duration: Duration) : HedgeDelay {
        override val initial: Duration get() = duration

        init {
            validateOrThrow(SUBJECT)
        }

        /**
         * Checks that [duration] is positive and at most [HARD_DELAY].
         *
         * @receiver the validation that collects the violations.
         * @since 1.0.0
         */
        override fun ValidationScope.validate() {
            positive(::duration, duration)
            inRange(property = ::duration, value = duration, range = Duration.ZERO..HARD_DELAY)
        }

        /**
         * Tells whether [other] is a fixed delay of the same duration.
         *
         * @param other the object to compare with.
         * @return true if [other] is equal.
         * @since 1.0.0
         */
        override fun equals(other: Any?): Boolean = other is Fixed && other.duration == duration

        /**
         * Returns a hash of the duration.
         *
         * @return the hash.
         * @since 1.0.0
         */
        override fun hashCode(): Int = duration.hashCode()

        /**
         * Shows the duration.
         *
         * @return the delay as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Fixed($duration)"
    }

    /**
     * A delay that follows the latency of the calls: the [percentile] of how long the last [window] primaries took
     * that succeeded, so that only the slowest calls are hedged.
     *
     * The hedge keeps the latencies in a ring of at most [window] entries, which never grows past [HARD_WINDOW]; a
     * new latency replaces the oldest one once the ring is full. Until it holds [minimumSamples] latencies the hedge
     * waits [fallback]. The percentile is taken by the nearest rank and is never shorter than [MINIMUM_DELAY] nor
     * longer than [HARD_DELAY].
     *
     * Only a primary that wins its call adds its latency, because a primary that loses to a hedge is cancelled before
     * it ends. The ring therefore leans towards fast primaries while hedges win often; choose a high [percentile] and
     * a [fallback] that suits the service.
     *
     * @property percentile the percentile as a fraction, above 0 and at most 1: 0.95 by default.
     * @property minimumSamples how many latencies the ring needs before the percentile applies, from 1 to [window]:
     *   100 by default.
     * @property window how many latencies the ring holds, from 1 to [HARD_WINDOW]: 1,000 by default.
     * @property fallback the delay while the ring holds too few latencies, positive and at most [HARD_DELAY]: 100
     *   milliseconds by default.
     * @since 1.0.0
     */
    public class Percentile internal constructor(
        public val percentile: Double,
        public val minimumSamples: Int,
        public val window: Int,
        public val fallback: Duration,
    ) : HedgeDelay {
        override val initial: Duration get() = fallback

        init {
            validateOrThrow(SUBJECT)
        }

        /**
         * Checks the bounds of every value.
         *
         * @receiver the validation that collects the violations.
         * @since 1.0.0
         */
        override fun ValidationScope.validate() {
            val isFraction = percentile > 0.0 && percentile <= 1.0
            if (!isFraction) fail(property = ::percentile, message = "must be above 0 and at most 1")
            inRange(property = ::window, value = window, range = 1..HARD_WINDOW)
            inRange(property = ::minimumSamples, value = minimumSamples, range = 1..window.coerceAtLeast(1))
            positive(::fallback, fallback)
            inRange(property = ::fallback, value = fallback, range = Duration.ZERO..HARD_DELAY)
        }

        /**
         * Tells whether [other] is a percentile delay with the same values.
         *
         * @param other the object to compare with.
         * @return true if [other] is equal.
         * @since 1.0.0
         */
        override fun equals(other: Any?): Boolean = other is Percentile &&
            other.percentile == percentile &&
            other.minimumSamples == minimumSamples &&
            other.window == window &&
            other.fallback == fallback

        /**
         * Returns a hash of the values.
         *
         * @return the hash.
         * @since 1.0.0
         */
        override fun hashCode(): Int {
            val head = percentile.hashCode() * HASH_FACTOR + minimumSamples
            return (head * HASH_FACTOR + window) * HASH_FACTOR + fallback.hashCode()
        }

        /**
         * Shows the values.
         *
         * @return the delay as text.
         * @since 1.0.0
         */
        override fun toString(): String =
            "Percentile($percentile, minimumSamples=$minimumSamples, window=$window, fallback=$fallback)"
    }

    /**
     * The hard ceilings and the defaults of a delay.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The longest delay of a hedge: one hour.
         *
         * @since 1.0.0
         */
        public val HARD_DELAY: Duration = 1.hours

        /**
         * The shortest delay a percentile yields: one millisecond, so that a hedge never starts its copies at once.
         *
         * @since 1.0.0
         */
        public val MINIMUM_DELAY: Duration = 1.milliseconds

        /**
         * The most latencies the ring of a percentile delay holds: 100,000.
         *
         * @since 1.0.0
         */
        public const val HARD_WINDOW: Int = 100_000

        internal const val DEFAULT_PERCENTILE: Double = 0.95
        internal const val DEFAULT_MINIMUM_SAMPLES: Int = 100
        internal const val DEFAULT_WINDOW: Int = 1_000
        internal val DEFAULT_DELAY: Duration = 100.milliseconds
        private const val SUBJECT: String = "hedge delay"
        private const val HASH_FACTOR: Int = 31
    }
}
