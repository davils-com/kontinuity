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

package com.davils.kontinuity.core.backoff

import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.positive
import com.davils.kore.core.validation.validateOrThrow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * How long to wait before the next attempt, such as the next retry of a call or the next trial of an open circuit
 * breaker: `exponential(initial = 1.seconds).jittered(Jitter.Full)`.
 *
 * A backoff is an immutable value, validated when it is created and safe to share between threads. Every delay it
 * yields is finite, at least zero and at most [maximum], which never exceeds [HARD_MAXIMUM_DELAY]. The randomness of a
 * [Jittered] backoff comes from the entropy of the environment, which defaults to secure randomness.
 *
 * @since 1.0.0
 */
public sealed interface Backoff : Validatable {
    /**
     * The longest delay this backoff yields.
     *
     * @since 1.0.0
     */
    public val maximum: Duration

    /**
     * The same delay before every attempt.
     *
     * @property delay the delay, from zero to [HARD_MAXIMUM_DELAY].
     * @since 1.0.0
     */
    public class Fixed internal constructor(public val delay: Duration) : Backoff {
        override val maximum: Duration get() = delay

        init {
            validateOrThrow(SUBJECT)
        }

        /**
         * Checks that [delay] is from zero to [HARD_MAXIMUM_DELAY].
         *
         * @receiver the validation that collects the violations.
         * @since 1.0.0
         */
        override fun ValidationScope.validate() {
            inRange(property = ::delay, value = delay, range = Duration.ZERO..HARD_MAXIMUM_DELAY)
        }

        /**
         * Tells whether [other] is a fixed backoff with the same delay.
         *
         * @param other the object to compare with.
         * @return true if [other] is equal.
         * @since 1.0.0
         */
        override fun equals(other: Any?): Boolean = other is Fixed && other.delay == delay

        /**
         * Returns a hash of the delay.
         *
         * @return the hash.
         * @since 1.0.0
         */
        override fun hashCode(): Int = delay.hashCode()

        /**
         * Shows the delay.
         *
         * @return the backoff as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Fixed($delay)"
    }

    /**
     * A delay that grows by [multiplier] with every attempt: `initial * multiplier^(attempt - 1)`, at most [maximum].
     *
     * @property initial the delay before the second attempt, positive and at most [maximum].
     * @property multiplier how much the delay grows per attempt, from 1 to [HARD_MULTIPLIER].
     * @property maximum the longest delay, at most [HARD_MAXIMUM_DELAY].
     * @since 1.0.0
     */
    public class Exponential internal constructor(
        public val initial: Duration,
        public val multiplier: Double,
        override val maximum: Duration,
    ) : Backoff {
        init {
            validateOrThrow(SUBJECT)
        }

        /**
         * Checks the bounds of [initial], [multiplier] and [maximum].
         *
         * @receiver the validation that collects the violations.
         * @since 1.0.0
         */
        override fun ValidationScope.validate() {
            positive(::initial, initial)
            inRange(property = ::initial, value = initial, range = Duration.ZERO..maximum)
            inRange(property = ::multiplier, value = multiplier, range = 1.0..HARD_MULTIPLIER)
            inRange(property = ::maximum, value = maximum, range = Duration.ZERO..HARD_MAXIMUM_DELAY)
        }

        /**
         * Tells whether [other] is an exponential backoff with the same values.
         *
         * @param other the object to compare with.
         * @return true if [other] is equal.
         * @since 1.0.0
         */
        override fun equals(other: Any?): Boolean = other is Exponential &&
            other.initial == initial &&
            other.multiplier == multiplier &&
            other.maximum == maximum

        /**
         * Returns a hash of the values.
         *
         * @return the hash.
         * @since 1.0.0
         */
        override fun hashCode(): Int = (initial.hashCode() * HASH_FACTOR + multiplier.hashCode()) *
            HASH_FACTOR +
            maximum.hashCode()

        /**
         * Shows the values.
         *
         * @return the backoff as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Exponential(initial=$initial, multiplier=$multiplier, maximum=$maximum)"
    }

    /**
     * The delays of [base], spread by [jitter] so that many callers do not attempt again at the same moment.
     *
     * @property base the backoff whose delays are spread.
     * @property jitter how the delays are spread.
     * @since 1.0.0
     */
    public class Jittered internal constructor(public val base: Backoff, public val jitter: Jitter) : Backoff {
        override val maximum: Duration get() = jitter.maximumOf(base.maximum)

        init {
            validateOrThrow(SUBJECT)
        }

        /**
         * Checks the factor of a [Jitter.Proportional]; the base was validated when it was created.
         *
         * @receiver the validation that collects the violations.
         * @since 1.0.0
         */
        override fun ValidationScope.validate() {
            jitter.validateIn(this)
        }

        /**
         * Tells whether [other] spreads the same base with the same jitter.
         *
         * @param other the object to compare with.
         * @return true if [other] is equal.
         * @since 1.0.0
         */
        override fun equals(other: Any?): Boolean = other is Jittered && other.base == base && other.jitter == jitter

        /**
         * Returns a hash of the base and the jitter.
         *
         * @return the hash.
         * @since 1.0.0
         */
        override fun hashCode(): Int = base.hashCode() * HASH_FACTOR + jitter.hashCode()

        /**
         * Shows the base and the jitter.
         *
         * @return the backoff as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Jittered($base, $jitter)"
    }

    /**
     * The hard ceilings of every backoff.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The longest delay of any backoff: one day.
         *
         * @since 1.0.0
         */
        public val HARD_MAXIMUM_DELAY: Duration = 1.days

        /**
         * The largest multiplier of an exponential backoff: 100.
         *
         * @since 1.0.0
         */
        public const val HARD_MULTIPLIER: Double = 100.0

        private const val SUBJECT: String = "backoff"
        private const val HASH_FACTOR: Int = 31
    }
}
