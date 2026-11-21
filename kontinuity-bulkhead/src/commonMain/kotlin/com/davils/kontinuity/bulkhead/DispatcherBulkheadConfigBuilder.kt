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

/**
 * Builds a [DispatcherBulkheadConfig]; every property starts from the configuration the builder starts from.
 *
 * A builder is meant for one thread; it refuses every change once it was evaluated.
 *
 * @since 1.0.0
 */
@DavilsDsl
public class DispatcherBulkheadConfigBuilder internal constructor() :
    ValidatingBuilder<DispatcherBulkheadConfig>() {
    override val subject: String get() = "dispatcher bulkhead configuration"

    /**
     * See [DispatcherBulkheadConfig.maxConcurrentCalls].
     *
     * @since 1.0.0
     */
    public var maxConcurrentCalls: Int = DispatcherBulkheadConfig.DEFAULT_CONCURRENT_CALLS
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [DispatcherBulkheadConfig.queueCapacity].
     *
     * @since 1.0.0
     */
    public var queueCapacity: Int = DispatcherBulkheadConfig.DEFAULT_QUEUE_CAPACITY
        set(value) {
            checkOpen()
            field = value
        }

    internal fun from(base: DispatcherBulkheadConfig) {
        maxConcurrentCalls = base.maxConcurrentCalls
        queueCapacity = base.queueCapacity
    }

    override fun assemble(): DispatcherBulkheadConfig = DispatcherBulkheadConfig(this)
}
