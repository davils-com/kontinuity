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

package com.davils.kontinuity.retry

import com.davils.kontinuity.core.backoff.Backoff
import com.davils.kontinuity.core.filter.FailureFilter
import com.davils.kontinuity.core.filter.FailureFilterBuilder
import com.davils.kontinuity.core.filter.ResultFilter
import com.davils.kontinuity.core.filter.ResultFilterBuilder
import com.davils.kore.core.annotation.DavilsDsl
import com.davils.kore.core.dsl.ValidatingBuilder
import com.davils.kontinuity.core.filter.failures as failureFilter
import com.davils.kontinuity.core.filter.results as resultFilter

/**
 * Builds a [RetryConfig]; every property starts from the configuration the builder starts from.
 *
 * @since 1.0.0
 */
@DavilsDsl
public class RetryConfigBuilder internal constructor() : ValidatingBuilder<RetryConfig>() {
    override val subject: String get() = "retry configuration"

    /**
     * See [RetryConfig.maxAttempts].
     *
     * @since 1.0.0
     */
    public var maxAttempts: Int = RetryConfig.DEFAULT_ATTEMPTS
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RetryConfig.backoff]; set it with `fixed`, `exponential` or `jittered` of kontinuity-core.
     *
     * @since 1.0.0
     */
    public var backoff: Backoff = RetryConfig.DEFAULT_BACKOFF
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RetryConfig.failures].
     *
     * @since 1.0.0
     */
    public var failures: FailureFilter = FailureFilter.RecordAll
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RetryConfig.results].
     *
     * @since 1.0.0
     */
    public var results: ResultFilter = ResultFilter.None
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RetryConfig.interval].
     *
     * @since 1.0.0
     */
    public var interval: RetryInterval? = null
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RetryConfig.isFailingAfterMaxAttempts].
     *
     * @since 1.0.0
     */
    public var isFailingAfterMaxAttempts: Boolean = false
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RetryConfig.onDiscardedResult].
     *
     * @since 1.0.0
     */
    public var onDiscardedResult: ((Any?) -> Unit)? = null
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RetryConfig.budget].
     *
     * @since 1.0.0
     */
    public var budget: RetryBudget? = null
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
     * Sets [results] from a block: `results { failIf<Response> { response -> response.status == 503 } }`.
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
     * Sets [interval] from a function: `interval { attempt, failure, result -> null }`.
     *
     * @param choose returns the wait before an attempt, or null for the backoff.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun interval(choose: RetryInterval) {
        interval = choose
    }

    internal fun from(base: RetryConfig) {
        maxAttempts = base.maxAttempts
        backoff = base.backoff
        failures = base.failures
        results = base.results
        interval = base.interval
        isFailingAfterMaxAttempts = base.isFailingAfterMaxAttempts
        onDiscardedResult = base.onDiscardedResult
        budget = base.budget
    }

    override fun assemble(): RetryConfig = RetryConfig(this)
}
