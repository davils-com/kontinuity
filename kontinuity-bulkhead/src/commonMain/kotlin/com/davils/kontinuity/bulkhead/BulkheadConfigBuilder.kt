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

package com.davils.kontinuity.bulkhead

import com.davils.kore.core.annotation.DavilsDsl
import com.davils.kore.core.dsl.ValidatingBuilder
import kotlin.time.Duration

/**
 * Builds a [BulkheadConfig]; every property starts from the configuration the builder starts from.
 *
 * A builder is meant for one thread; it refuses every change once it was evaluated.
 *
 * @since 1.0.0
 */
@DavilsDsl
public class BulkheadConfigBuilder internal constructor() : ValidatingBuilder<BulkheadConfig>() {
    override val subject: String get() = "bulkhead configuration"

    /**
     * See [BulkheadConfig.maxConcurrentCalls].
     *
     * @since 1.0.0
     */
    public var maxConcurrentCalls: Int = BulkheadConfig.DEFAULT_CONCURRENT_CALLS
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [BulkheadConfig.maxWaitDuration].
     *
     * @since 1.0.0
     */
    public var maxWaitDuration: Duration = Duration.ZERO
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [BulkheadConfig.maxWaitingCalls].
     *
     * @since 1.0.0
     */
    public var maxWaitingCalls: Int = BulkheadConfig.DEFAULT_WAITING_CALLS
        set(value) {
            checkOpen()
            field = value
        }

    internal fun from(base: BulkheadConfig) {
        maxConcurrentCalls = base.maxConcurrentCalls
        maxWaitDuration = base.maxWaitDuration
        maxWaitingCalls = base.maxWaitingCalls
    }

    override fun assemble(): BulkheadConfig = BulkheadConfig(this)
}
