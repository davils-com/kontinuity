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
import com.davils.kontinuity.core.filter.FailureFilter
import com.davils.kontinuity.core.filter.FailureFilterBuilder
import com.davils.kontinuity.core.filter.ResultFilter
import com.davils.kontinuity.core.filter.ResultFilterBuilder
import com.davils.kore.core.annotation.DavilsDsl
import com.davils.kore.core.dsl.ValidatingBuilder
import kotlin.time.Duration
import com.davils.kontinuity.core.filter.failures as failureFilter
import com.davils.kontinuity.core.filter.results as resultFilter

/**
 * Builds a [CircuitBreakerConfig]; every property starts from the configuration the builder starts from.
 *
 * @since 1.0.0
 */
@DavilsDsl
public class CircuitBreakerConfigBuilder internal constructor() : ValidatingBuilder<CircuitBreakerConfig>() {
    override val subject: String get() = "circuit breaker configuration"

    /**
     * See [CircuitBreakerConfig.failureRateThreshold].
     *
     * @since 1.0.0
     */
    public var failureRateThreshold: Double = CircuitBreakerConfig.DEFAULT_FAILURE_RATE
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CircuitBreakerConfig.slowCallRateThreshold].
     *
     * @since 1.0.0
     */
    public var slowCallRateThreshold: Double = CircuitBreakerConfig.DEFAULT_SLOW_CALL_RATE
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CircuitBreakerConfig.slowCallDurationThreshold].
     *
     * @since 1.0.0
     */
    public var slowCallDurationThreshold: Duration = CircuitBreakerConfig.DEFAULT_SLOW_CALL_DURATION
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CircuitBreakerConfig.slidingWindow]; set it with [countBased] or [timeBased].
     *
     * @since 1.0.0
     */
    public var slidingWindow: SlidingWindow = SlidingWindow.CountBased(CircuitBreakerConfig.DEFAULT_WINDOW_SIZE)
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CircuitBreakerConfig.minimumNumberOfCalls].
     *
     * @since 1.0.0
     */
    public var minimumNumberOfCalls: Int = CircuitBreakerConfig.DEFAULT_MINIMUM_CALLS
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CircuitBreakerConfig.permittedCallsInHalfOpenState].
     *
     * @since 1.0.0
     */
    public var permittedCallsInHalfOpenState: Int = CircuitBreakerConfig.DEFAULT_TRIAL_CALLS
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CircuitBreakerConfig.maxWaitInHalfOpenState].
     *
     * @since 1.0.0
     */
    public var maxWaitInHalfOpenState: Duration = Duration.INFINITE
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CircuitBreakerConfig.stateAfterHalfOpenTimeout].
     *
     * @since 1.0.0
     */
    public var stateAfterHalfOpenTimeout: CircuitState = CircuitState.Open
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CircuitBreakerConfig.waitInOpenState].
     *
     * @since 1.0.0
     */
    public var waitInOpenState: Backoff = CircuitBreakerConfig.DEFAULT_WAIT
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CircuitBreakerConfig.isAutomaticTransitionEnabled].
     *
     * @since 1.0.0
     */
    public var isAutomaticTransitionEnabled: Boolean = false
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CircuitBreakerConfig.failures].
     *
     * @since 1.0.0
     */
    public var failures: FailureFilter = FailureFilter.RecordAll
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CircuitBreakerConfig.results].
     *
     * @since 1.0.0
     */
    public var results: ResultFilter = ResultFilter.None
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CircuitBreakerConfig.openingFailures].
     *
     * @since 1.0.0
     */
    public var openingFailures: FailureFilter? = null
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CircuitBreakerConfig.openingResults].
     *
     * @since 1.0.0
     */
    public var openingResults: ResultFilter = ResultFilter.None
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * Sets [failures] from a block: `failures { record<IOException>(); ignore<BusinessException>() }`.
     *
     * @param configure declares the rules.
     * @throws com.davils.kore.core.validation.report.ValidationException if the filter has too many rules.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun failures(configure: FailureFilterBuilder.() -> Unit) {
        failures = failureFilter(configure)
    }

    /**
     * Sets [results] from a block: `results { failIf<Response> { response -> response.status >= 500 } }`.
     *
     * @param configure declares the rules.
     * @throws com.davils.kore.core.validation.report.ValidationException if the filter has too many rules.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun results(configure: ResultFilterBuilder.() -> Unit) {
        results = resultFilter(configure)
    }

    /**
     * Sets [openingFailures] from a block; a failure the filter records opens the breaker at once.
     *
     * @param configure declares the rules.
     * @throws com.davils.kore.core.validation.report.ValidationException if the filter has too many rules.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun openOnFailures(configure: FailureFilterBuilder.() -> Unit) {
        openingFailures = failureFilter(configure)
    }

    /**
     * Sets [openingResults] from a block; a result the filter accepts opens the breaker at once.
     *
     * @param configure declares the rules.
     * @throws com.davils.kore.core.validation.report.ValidationException if the filter has too many rules.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun openOnResults(configure: ResultFilterBuilder.() -> Unit) {
        openingResults = resultFilter(configure)
    }

    internal fun from(base: CircuitBreakerConfig) {
        failureRateThreshold = base.failureRateThreshold
        slowCallRateThreshold = base.slowCallRateThreshold
        slowCallDurationThreshold = base.slowCallDurationThreshold
        slidingWindow = base.slidingWindow
        minimumNumberOfCalls = base.minimumNumberOfCalls
        permittedCallsInHalfOpenState = base.permittedCallsInHalfOpenState
        maxWaitInHalfOpenState = base.maxWaitInHalfOpenState
        stateAfterHalfOpenTimeout = base.stateAfterHalfOpenTimeout
        waitInOpenState = base.waitInOpenState
        isAutomaticTransitionEnabled = base.isAutomaticTransitionEnabled
        failures = base.failures
        results = base.results
        openingFailures = base.openingFailures
        openingResults = base.openingResults
    }

    override fun assemble(): CircuitBreakerConfig = CircuitBreakerConfig(this)
}
