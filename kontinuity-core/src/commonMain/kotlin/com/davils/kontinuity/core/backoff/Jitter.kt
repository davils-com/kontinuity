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

import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import kotlin.time.Duration

/**
 * How a [Backoff.Jittered] spreads the delays of its base, as described in "Exponential Backoff And Jitter" of AWS.
 *
 * @since 1.0.0
 */
public sealed interface Jitter {
    /**
     * A uniform delay within `[delay * (1 - factor), delay * (1 + factor))`, as resilience4j's randomized wait does.
     *
     * @property factor how far a delay may move, in `(0, 1]`.
     * @since 1.0.0
     */
    public class Proportional internal constructor(public val factor: Double) : Jitter {
        /**
         * Tells whether [other] is a proportional jitter with the same factor.
         *
         * @param other the object to compare with.
         * @return true if [other] is equal.
         * @since 1.0.0
         */
        override fun equals(other: Any?): Boolean = other is Proportional && other.factor == factor

        /**
         * Returns a hash of the factor.
         *
         * @return the hash.
         * @since 1.0.0
         */
        override fun hashCode(): Int = factor.hashCode()

        /**
         * Shows the factor.
         *
         * @return the jitter as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Proportional($factor)"
    }

    /**
     * A uniform delay within `[0, delay)`: "full jitter".
     *
     * @since 1.0.0
     */
    public object Full : Jitter {
        /**
         * Shows the name of the jitter.
         *
         * @return `Full`.
         * @since 1.0.0
         */
        override fun toString(): String = "Full"
    }

    /**
     * Half the delay plus a uniform share of the other half: "equal jitter".
     *
     * @since 1.0.0
     */
    public object Equal : Jitter {
        /**
         * Shows the name of the jitter.
         *
         * @return `Equal`.
         * @since 1.0.0
         */
        override fun toString(): String = "Equal"
    }

    /**
     * A uniform delay between the first delay of the base and three times the previous delay, at most the maximum of
     * the base: "decorrelated jitter".
     *
     * @since 1.0.0
     */
    public object Decorrelated : Jitter {
        /**
         * Shows the name of the jitter.
         *
         * @return `Decorrelated`.
         * @since 1.0.0
         */
        override fun toString(): String = "Decorrelated"
    }
}

internal fun Jitter.maximumOf(base: Duration): Duration = when (this) {
    is Jitter.Proportional -> base * (1.0 + factor)
    Jitter.Full, Jitter.Equal, Jitter.Decorrelated -> base
}

internal fun Jitter.validateIn(scope: ValidationScope) {
    if (this is Jitter.Proportional) {
        scope.inRange(property = Jitter.Proportional::factor, value = factor, range = Double.MIN_VALUE..1.0)
    }
}
