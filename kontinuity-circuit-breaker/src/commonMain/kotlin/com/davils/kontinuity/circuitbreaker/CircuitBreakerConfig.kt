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

import com.davils.kontinuity.core.backoff.Backoff
import com.davils.kontinuity.core.backoff.fixed
import com.davils.kontinuity.core.filter.FailureFilter
import com.davils.kontinuity.core.filter.ResultFilter
import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.validateOrThrow
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private const val SUBJECT: String = "circuit breaker configuration"
private const val PERCENT: Double = 100.0

/**
 * How a [CircuitBreaker] decides, with the defaults of resilience4j.
 *
 * A configuration is an immutable value, validated when it is built and safe to share between breakers and threads.
 * A registry hands the same configuration to many breakers.
 *
 * @since 1.0.0
 */
public class CircuitBreakerConfig internal constructor(builder: CircuitBreakerConfigBuilder) : Validatable {
    /**
     * The failure rate in percent at which a closed breaker opens: 50 by default, in `(0, 100]`.
     *
     * @since 1.0.0
     */
    public val failureRateThreshold: Double = builder.failureRateThreshold

    /**
     * The rate of slow calls in percent at which a closed breaker opens: 100 by default, in `(0, 100]`.
     *
     * @since 1.0.0
     */
    public val slowCallRateThreshold: Double = builder.slowCallRateThreshold

    /**
     * How long a call may take before it counts as slow: one minute by default, positive and at most one day.
     *
     * @since 1.0.0
     */
    public val slowCallDurationThreshold: Duration = builder.slowCallDurationThreshold

    /**
     * The window of recent calls: the last 100 calls by default.
     *
     * @since 1.0.0
     */
    public val slidingWindow: SlidingWindow = builder.slidingWindow

    /**
     * How many calls the window needs before a rate is computed: 100 by default, at least 1 and, for a count-based
     * window, at most its size.
     *
     * @since 1.0.0
     */
    public val minimumNumberOfCalls: Int = builder.minimumNumberOfCalls

    /**
     * How many trial calls a half-open breaker lets through: 10 by default, from 1 to [HARD_TRIAL_CALLS].
     *
     * @since 1.0.0
     */
    public val permittedCallsInHalfOpenState: Int = builder.permittedCallsInHalfOpenState

    /**
     * How long a breaker stays half-open before it moves to [stateAfterHalfOpenTimeout]: infinite by default, so
     * that it waits for its trial calls.
     *
     * @since 1.0.0
     */
    public val maxWaitInHalfOpenState: Duration = builder.maxWaitInHalfOpenState

    /**
     * The state a half-open breaker moves to when [maxWaitInHalfOpenState] is over: [CircuitState.Open] by default;
     * only [CircuitState.Open] and [CircuitState.Closed] are allowed.
     *
     * @since 1.0.0
     */
    public val stateAfterHalfOpenTimeout: CircuitState = builder.stateAfterHalfOpenTimeout

    /**
     * How long an open breaker waits before it lets trial calls through: one minute, fixed, by default. The attempt
     * of the backoff counts the openings in a row, so an exponential backoff waits longer after every failed trial.
     *
     * @since 1.0.0
     */
    public val waitInOpenState: Backoff = builder.waitInOpenState

    /**
     * Whether an open breaker moves to half-open when its wait is over without waiting for the next call: false by
     * default. It needs the scope of the environment.
     *
     * @since 1.0.0
     */
    public val isAutomaticTransitionEnabled: Boolean = builder.isAutomaticTransitionEnabled

    /**
     * Which failures count as failures: every failure but the rejections of kontinuity by default. A failure that the
     * filter passes counts as a success, an ignored one counts as nothing.
     *
     * @since 1.0.0
     */
    public val failures: FailureFilter = builder.failures

    /**
     * Which results count as failures: none by default.
     *
     * @since 1.0.0
     */
    public val results: ResultFilter = builder.results

    /**
     * Which failures open the breaker at once, whatever the rates: none by default.
     *
     * @since 1.0.0
     */
    public val openingFailures: FailureFilter? = builder.openingFailures

    /**
     * Which results open the breaker at once, whatever the rates: none by default.
     *
     * @since 1.0.0
     */
    public val openingResults: ResultFilter = builder.openingResults

    init {
        validateOrThrow(SUBJECT)
    }

    /**
     * Checks every value against its bounds.
     *
     * @receiver the validation that collects the violations.
     * @since 1.0.0
     */
    override fun ValidationScope.validate() {
        percent(::failureRateThreshold, failureRateThreshold)
        percent(::slowCallRateThreshold, slowCallRateThreshold)
        if (!slowCallDurationThreshold.isPositive()) fail(property = ::slowCallDurationThreshold, message = POSITIVE)
        inRange(
            property = ::slowCallDurationThreshold,
            value = slowCallDurationThreshold,
            range = Duration.ZERO..1.days,
        )
        inRange(property = ::minimumNumberOfCalls, value = minimumNumberOfCalls, range = 1..maximumCalls())
        inRange(
            property = ::permittedCallsInHalfOpenState,
            value = permittedCallsInHalfOpenState,
            range = 1..HARD_TRIAL_CALLS,
        )
        if (!maxWaitInHalfOpenState.isPositive()) fail(property = ::maxWaitInHalfOpenState, message = POSITIVE)
        val isAllowedTarget = stateAfterHalfOpenTimeout == CircuitState.Open ||
            stateAfterHalfOpenTimeout == CircuitState.Closed
        if (!isAllowedTarget) fail(property = ::stateAfterHalfOpenTimeout, message = "must be Open or Closed")
    }

    /**
     * Shows the thresholds, the window and the waits.
     *
     * @return the configuration as text.
     * @since 1.0.0
     */
    override fun toString(): String = "CircuitBreakerConfig(failureRateThreshold=$failureRateThreshold, " +
        "slowCallRateThreshold=$slowCallRateThreshold, slowCallDurationThreshold=$slowCallDurationThreshold, " +
        "slidingWindow=$slidingWindow, minimumNumberOfCalls=$minimumNumberOfCalls, " +
        "permittedCallsInHalfOpenState=$permittedCallsInHalfOpenState, waitInOpenState=$waitInOpenState)"

    private fun maximumCalls(): Int = when (val window = slidingWindow) {
        is SlidingWindow.CountBased -> window.size
        is SlidingWindow.TimeBased -> Int.MAX_VALUE
    }

    private fun ValidationScope.percent(property: KProperty<*>, value: Double) {
        val isInRange = value > 0.0 && value <= PERCENT
        if (!isInRange) fail(property = property, message = "must be above 0 and at most 100")
    }

    /**
     * The hard ceiling and the default configuration.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The most trial calls of a half-open breaker: 10,000.
         *
         * @since 1.0.0
         */
        public const val HARD_TRIAL_CALLS: Int = 10_000

        internal const val DEFAULT_FAILURE_RATE: Double = 50.0
        internal const val DEFAULT_SLOW_CALL_RATE: Double = 100.0
        internal const val DEFAULT_WINDOW_SIZE: Int = 100
        internal const val DEFAULT_MINIMUM_CALLS: Int = 100
        internal const val DEFAULT_TRIAL_CALLS: Int = 10
        internal val DEFAULT_SLOW_CALL_DURATION: Duration = 1.minutes
        internal val DEFAULT_WAIT: Backoff = fixed(1.minutes)
        private const val POSITIVE: String = "must be positive"

        /**
         * The configuration with the defaults of resilience4j.
         *
         * @since 1.0.0
         */
        public val Default: CircuitBreakerConfig = CircuitBreakerConfig(CircuitBreakerConfigBuilder())
    }
}
