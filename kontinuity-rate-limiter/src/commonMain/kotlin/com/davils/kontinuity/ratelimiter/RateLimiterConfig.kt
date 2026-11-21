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

import com.davils.kontinuity.core.filter.FailureFilter
import com.davils.kontinuity.core.filter.ResultFilter
import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.positive
import com.davils.kore.core.validation.validateOrThrow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SUBJECT: String = "rate limiter configuration"

/**
 * How a [RateLimiter] hands out its permits, with the defaults of resilience4j where they are safe.
 *
 * A configuration is an immutable value, validated when it is built and safe to share between limiters and threads.
 * Every value is bounded by a hard ceiling that no configuration exceeds, so that neither a broken configuration nor
 * an attacker who controls one can make a limiter wait forever, hold unbounded state or overflow its arithmetic.
 *
 * @since 1.0.0
 */
public class RateLimiterConfig internal constructor(builder: RateLimiterConfigBuilder) : Validatable {
    /**
     * How many permits one period hands out: 50 by default, from 1 to [HARD_LIMIT_FOR_PERIOD]. A single call asks
     * for at most this many permits. [RateLimiter.changeLimitForPeriod] changes it at runtime.
     *
     * @since 1.0.0
     */
    public val limitForPeriod: Int = builder.limitForPeriod

    /**
     * How long one period lasts: 500 milliseconds by default, positive and at most [HARD_REFRESH_PERIOD].
     *
     * resilience4j defaults to 500 nanoseconds, which turns the limit into a rate per half microsecond and is rarely
     * what a caller means; kontinuity defaults to 500 milliseconds, 50 permits every half second.
     *
     * @since 1.0.0
     */
    public val limitRefreshPeriod: Duration = builder.limitRefreshPeriod

    /**
     * How long a caller waits for its permits at most: 5 seconds by default, from zero to [HARD_TIMEOUT]. A caller
     * whose permits are free later than this is rejected at once instead of waiting. Zero means callers never wait.
     * [RateLimiter.changeTimeout] changes it at runtime.
     *
     * @since 1.0.0
     */
    public val timeout: Duration = builder.timeout

    /**
     * How the permits are counted: [RateLimiterAlgorithm.FixedWindow] by default.
     *
     * @since 1.0.0
     */
    public val algorithm: RateLimiterAlgorithm = builder.algorithm

    /**
     * How many callers may wait for permits at once: 10,000 by default, from 0 to [HARD_WAITING_CALLERS]. A caller
     * that would have to wait while the queue is full is rejected at once; with 0, callers never wait.
     *
     * @since 1.0.0
     */
    public val maxWaitingCallers: Int = builder.maxWaitingCallers

    /**
     * Which failures of a call drain the remaining permits of the current period: none by default. A failure the
     * filter records drains them, like `drainPermissionsOnResult` of resilience4j, such as when the protected service
     * answers that its own limit is reached. Rejections of kontinuity never drain.
     *
     * @since 1.0.0
     */
    public val drainOnFailures: FailureFilter? = builder.drainOnFailures

    /**
     * Which results of a call drain the remaining permits of the current period: none by default.
     *
     * @since 1.0.0
     */
    public val drainOnResults: ResultFilter = builder.drainOnResults

    init {
        validateOrThrow(SUBJECT)
    }

    /**
     * Checks every value against its bounds and a token bucket against the limit and the period.
     *
     * @receiver the validation that collects the violations.
     * @since 1.0.0
     */
    override fun ValidationScope.validate() {
        inRange(property = ::limitForPeriod, value = limitForPeriod, range = 1..HARD_LIMIT_FOR_PERIOD)
        positive(property = ::limitRefreshPeriod, value = limitRefreshPeriod)
        inRange(
            property = ::limitRefreshPeriod,
            value = limitRefreshPeriod,
            range = Duration.ZERO..HARD_REFRESH_PERIOD,
        )
        inRange(property = ::timeout, value = timeout, range = Duration.ZERO..HARD_TIMEOUT)
        inRange(property = ::maxWaitingCallers, value = maxWaitingCallers, range = 0..HARD_WAITING_CALLERS)
        val bucket = algorithm as? RateLimiterAlgorithm.TokenBucket
        val isUnfit = bucket != null && !bucket.fits(limitForPeriod, limitRefreshPeriod)
        if (isUnfit) fail(property = ::algorithm, message = "must hold the limit and refill within ten years")
    }

    /**
     * Shows every value but the filters.
     *
     * @return the configuration as text.
     * @since 1.0.0
     */
    override fun toString(): String = "RateLimiterConfig(limitForPeriod=$limitForPeriod, " +
        "limitRefreshPeriod=$limitRefreshPeriod, timeout=$timeout, algorithm=$algorithm, " +
        "maxWaitingCallers=$maxWaitingCallers)"

    /**
     * The hard ceilings and the default configuration.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The most permits per period: 1,000,000.
         *
         * @since 1.0.0
         */
        public const val HARD_LIMIT_FOR_PERIOD: Int = 1_000_000

        /**
         * The longest period: one day.
         *
         * @since 1.0.0
         */
        public val HARD_REFRESH_PERIOD: Duration = 1.days

        /**
         * The longest wait of a caller: one hour.
         *
         * @since 1.0.0
         */
        public val HARD_TIMEOUT: Duration = 1.hours

        /**
         * The most callers waiting at once: 1,000,000.
         *
         * @since 1.0.0
         */
        public const val HARD_WAITING_CALLERS: Int = 1_000_000

        internal const val DEFAULT_LIMIT_FOR_PERIOD: Int = 50
        internal const val DEFAULT_WAITING_CALLERS: Int = 10_000
        internal val DEFAULT_REFRESH_PERIOD: Duration = 500.milliseconds
        internal val DEFAULT_TIMEOUT: Duration = 5.seconds

        /**
         * The default configuration: 50 permits every 500 milliseconds in fixed windows, a wait of at most 5 seconds
         * and at most 10,000 waiting callers.
         *
         * @since 1.0.0
         */
        public val Default: RateLimiterConfig = RateLimiterConfig(RateLimiterConfigBuilder())
    }
}
