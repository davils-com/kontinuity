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
import com.davils.kontinuity.core.filter.FailureFilterBuilder
import com.davils.kontinuity.core.filter.ResultFilter
import com.davils.kontinuity.core.filter.ResultFilterBuilder
import com.davils.kore.core.annotation.DavilsDsl
import com.davils.kore.core.dsl.ValidatingBuilder
import kotlin.time.Duration
import com.davils.kontinuity.core.filter.failures as failureFilter
import com.davils.kontinuity.core.filter.results as resultFilter

/**
 * Builds a [RateLimiterConfig]; every property starts from the configuration the builder starts from.
 *
 * @since 1.0.0
 */
@DavilsDsl
public class RateLimiterConfigBuilder internal constructor() : ValidatingBuilder<RateLimiterConfig>() {
    override val subject: String get() = "rate limiter configuration"

    /**
     * See [RateLimiterConfig.limitForPeriod].
     *
     * @since 1.0.0
     */
    public var limitForPeriod: Int = RateLimiterConfig.DEFAULT_LIMIT_FOR_PERIOD
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RateLimiterConfig.limitRefreshPeriod].
     *
     * @since 1.0.0
     */
    public var limitRefreshPeriod: Duration = RateLimiterConfig.DEFAULT_REFRESH_PERIOD
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RateLimiterConfig.timeout].
     *
     * @since 1.0.0
     */
    public var timeout: Duration = RateLimiterConfig.DEFAULT_TIMEOUT
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RateLimiterConfig.algorithm]; set it to [RateLimiterAlgorithm.FixedWindow],
     * [RateLimiterAlgorithm.SlidingWindow] or [tokenBucket].
     *
     * @since 1.0.0
     */
    public var algorithm: RateLimiterAlgorithm = RateLimiterAlgorithm.FixedWindow
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RateLimiterConfig.maxWaitingCallers].
     *
     * @since 1.0.0
     */
    public var maxWaitingCallers: Int = RateLimiterConfig.DEFAULT_WAITING_CALLERS
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RateLimiterConfig.drainOnFailures].
     *
     * @since 1.0.0
     */
    public var drainOnFailures: FailureFilter? = null
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RateLimiterConfig.drainOnResults].
     *
     * @since 1.0.0
     */
    public var drainOnResults: ResultFilter = ResultFilter.None
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * Sets [drainOnFailures] from a block: `drainOnFailures { record<QuotaExceededException>() }`.
     *
     * @param configure declares the rules.
     * @throws com.davils.kore.core.validation.report.ValidationException if the filter has too many rules.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun drainOnFailures(configure: FailureFilterBuilder.() -> Unit) {
        drainOnFailures = failureFilter(configure)
    }

    /**
     * Sets [drainOnResults] from a block: `drainOnResults { failIf<Response> { response -> response.status == 429 } }`.
     *
     * @param configure declares the rules; a result a rule accepts drains the permits.
     * @throws com.davils.kore.core.validation.report.ValidationException if the filter has too many rules.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun drainOnResults(configure: ResultFilterBuilder.() -> Unit) {
        drainOnResults = resultFilter(configure)
    }

    internal fun from(base: RateLimiterConfig) {
        limitForPeriod = base.limitForPeriod
        limitRefreshPeriod = base.limitRefreshPeriod
        timeout = base.timeout
        algorithm = base.algorithm
        maxWaitingCallers = base.maxWaitingCallers
        drainOnFailures = base.drainOnFailures
        drainOnResults = base.drainOnResults
    }

    override fun assemble(): RateLimiterConfig = RateLimiterConfig(this)
}
