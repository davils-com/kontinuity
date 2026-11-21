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
import com.davils.kore.core.validation.validateOrThrow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

private const val SUBJECT: String = "time limiter configuration"

/**
 * How a [TimeLimiter] limits its calls, with the defaults of resilience4j: a timeout of one second, ended with the
 * [TimeoutStrategy.Hard] strategy.
 *
 * A configuration is an immutable value, validated when it is built and safe to share between limiters and threads.
 * A registry hands the same configuration to many limiters.
 *
 * @since 1.0.0
 */
public class TimeLimiterConfig internal constructor(builder: TimeLimiterConfigBuilder) : Validatable {
    /**
     * How long a call may take: one second by default, positive and at most [HARD_TIMEOUT], or [Duration.INFINITE]
     * for no limit, in which case the call runs in the coroutine of its caller whatever the [strategy].
     *
     * @since 1.0.0
     */
    public val timeout: Duration = builder.timeout

    /**
     * How a call that runs out of time ends: [TimeoutStrategy.Hard] by default. A [TimeoutStrategy.Soft] strategy
     * needs the scope of the environment of the limiter.
     *
     * @since 1.0.0
     */
    public val strategy: TimeoutStrategy = builder.strategy

    init {
        validateOrThrow(SUBJECT)
    }

    /**
     * Checks that [timeout] is positive and at most [HARD_TIMEOUT] or infinite.
     *
     * @receiver the validation that collects the violations.
     * @since 1.0.0
     */
    override fun ValidationScope.validate() {
        val isPositive = timeout.isPositive()
        if (!isPositive) fail(property = ::timeout, message = "must be positive")
        val isBounded = timeout.isInfinite() || timeout <= HARD_TIMEOUT
        if (!isBounded) fail(property = ::timeout, message = "must be at most one day or infinite")
    }

    /**
     * Shows the timeout and the strategy.
     *
     * @return the configuration as text.
     * @since 1.0.0
     */
    override fun toString(): String = "TimeLimiterConfig(timeout=$timeout, strategy=$strategy)"

    /**
     * The hard ceiling and the default configuration.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The longest finite timeout: one day.
         *
         * @since 1.0.0
         */
        public val HARD_TIMEOUT: Duration = 1.days

        internal val DEFAULT_TIMEOUT: Duration = 1.seconds

        /**
         * The configuration with the defaults of resilience4j.
         *
         * @since 1.0.0
         */
        public val Default: TimeLimiterConfig = TimeLimiterConfig(TimeLimiterConfigBuilder())
    }
}
