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

import com.davils.kontinuity.core.filter.FailureFilter
import com.davils.kontinuity.core.filter.FailureFilterBuilder
import com.davils.kore.core.annotation.DavilsDsl
import com.davils.kore.core.dsl.ValidatingBuilder
import com.davils.kontinuity.core.filter.failures as failureFilter

/**
 * Builds a [HedgeConfig]; every property starts from the configuration the builder starts from.
 *
 * @since 1.0.0
 */
@DavilsDsl
public class HedgeConfigBuilder internal constructor() : ValidatingBuilder<HedgeConfig>() {
    override val subject: String get() = "hedge configuration"

    /**
     * See [HedgeConfig.delay]; set it with [fixedDelay] or [percentileDelay].
     *
     * @since 1.0.0
     */
    public var delay: HedgeDelay = HedgeDelay.Fixed(HedgeDelay.DEFAULT_DELAY)
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [HedgeConfig.maxHedgedAttempts].
     *
     * @since 1.0.0
     */
    public var maxHedgedAttempts: Int = HedgeConfig.DEFAULT_HEDGED_ATTEMPTS
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [HedgeConfig.failures].
     *
     * @since 1.0.0
     */
    public var failures: FailureFilter = FailureFilter.RecordAll
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

    internal fun from(base: HedgeConfig) {
        delay = base.delay
        maxHedgedAttempts = base.maxHedgedAttempts
        failures = base.failures
    }

    override fun assemble(): HedgeConfig = HedgeConfig(this)
}
