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

import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.validateOrThrow

/**
 * How a [TimeLimiter] ends a call that runs out of time: [Hard] cancels it where it runs, [Soft] stops waiting for
 * it and leaves it running detached on the scope of the environment.
 *
 * @since 1.0.0
 */
public sealed interface TimeoutStrategy {
    /**
     * The call runs in the coroutine of its caller inside `withTimeout`; at the timeout it is cancelled and the
     * caller receives a `CallTimeoutException` once the call has ended.
     *
     * Cancellation is cooperative: a call that does not suspend or check for cancellation, such as a busy loop or a
     * blocking read, ends only when it returns by itself, and the timeout reaches the caller that much later. Run
     * such calls with [Soft] or through `executeBlocking` on the JVM, which interrupts the blocked thread. This is the
     * default strategy.
     *
     * @since 1.0.0
     */
    public object Hard : TimeoutStrategy {
        /**
         * Shows the name of the strategy.
         *
         * @return `Hard`.
         * @since 1.0.0
         */
        override fun toString(): String = "Hard"
    }

    /**
     * The call runs detached on the scope of the environment and its caller stops waiting at the timeout, as the
     * `TimeLimiter` of resilience4j does with a future.
     *
     * The detached call keeps the elements of the context of its caller, such as its name, but neither its `Job` nor
     * its dispatcher: it runs on the dispatcher of the scope, and the cancellation of the scope cancels it. When the
     * caller stops waiting, at the timeout or by its own cancellation, the detached call is cancelled if
     * [isCancellingOnTimeout] says so and keeps running to its end otherwise; a failure it ends with after that is
     * counted and reported as a detached failure, never thrown. The failure of a detached call never cancels the
     * scope.
     *
     * Every call of a soft limiter holds one of [maxDetached] slots from its start until its detached work has ended,
     * whether its caller still waits for it or not; a call that finds every slot taken is refused at once with a
     * [DetachedLimitExceededException]. This bounds the work an attacker can leave behind with calls that never end.
     *
     * @property isCancellingOnTimeout whether the detached call is cancelled when its caller stops waiting: true by
     *   default, like `cancelRunningFuture` of resilience4j.
     * @property maxDetached how many calls may run detached at once: 1,000 by default, from 1 to [HARD_MAX_DETACHED].
     * @throws com.davils.kore.core.validation.report.ValidationException if [maxDetached] is out of its bounds.
     * @since 1.0.0
     */
    public class Soft(
        public val isCancellingOnTimeout: Boolean = true,
        public val maxDetached: Int = DEFAULT_MAX_DETACHED,
    ) : TimeoutStrategy, Validatable {
        init {
            validateOrThrow("soft timeout strategy")
        }

        /**
         * Checks that [maxDetached] is from 1 to [HARD_MAX_DETACHED].
         *
         * @receiver the validation that collects the violations.
         * @since 1.0.0
         */
        override fun ValidationScope.validate() {
            inRange(property = ::maxDetached, value = maxDetached, range = 1..HARD_MAX_DETACHED)
        }

        /**
         * Tells whether [other] is a soft strategy with the same values.
         *
         * @param other the object to compare with.
         * @return true if [other] is equal.
         * @since 1.0.0
         */
        override fun equals(other: Any?): Boolean =
            other is Soft && other.isCancellingOnTimeout == isCancellingOnTimeout && other.maxDetached == maxDetached

        /**
         * Returns a hash of the values.
         *
         * @return the hash.
         * @since 1.0.0
         */
        override fun hashCode(): Int = maxDetached * HASH_FACTOR + isCancellingOnTimeout.hashCode()

        /**
         * Shows the values.
         *
         * @return the strategy as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Soft(isCancellingOnTimeout=$isCancellingOnTimeout, maxDetached=$maxDetached)"
    }

    /**
     * The defaults and the hard ceiling of a soft strategy.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The most detached calls of a soft strategy: 100,000.
         *
         * @since 1.0.0
         */
        public const val HARD_MAX_DETACHED: Int = 100_000

        /**
         * The detached calls of a soft strategy by default: 1,000.
         *
         * @since 1.0.0
         */
        public const val DEFAULT_MAX_DETACHED: Int = 1_000

        private const val HASH_FACTOR: Int = 31
    }
}
