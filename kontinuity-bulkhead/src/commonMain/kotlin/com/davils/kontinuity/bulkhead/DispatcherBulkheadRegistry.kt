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

@file:OptIn(DavilsInternal::class)

package com.davils.kontinuity.bulkhead

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.registry.Instances
import com.davils.kontinuity.core.registry.Registry
import com.davils.kontinuity.core.registry.RegistrySettings
import com.davils.kore.core.annotation.DavilsInternal
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The named dispatcher bulkheads of an application and the configurations they are created from.
 *
 * Create one with `dispatcherBulkheadRegistry(Dispatchers.IO) { defaults { maxConcurrentCalls = 4 } }`. A registry
 * creates a bulkhead at most once per name, hands every bulkhead its [dispatcher] and its [environment] and holds at
 * most the instances its limits allow. Each bulkhead limits its own view of the dispatcher, so the bulkheads of a
 * registry share its threads but not their limits. A registry is safe to use from many coroutines and threads at once.
 *
 * @param instances the instances the registry delegates to.
 * @property dispatcher the dispatcher of the caller that runs the calls of every bulkhead of the registry.
 * @property environment the environment of every bulkhead of the registry.
 * @since 1.0.0
 */
public class DispatcherBulkheadRegistry internal constructor(
    private val instances: Instances<DispatcherBulkheadConfig, DispatcherBulkhead>,
    public val dispatcher: CoroutineDispatcher,
    public val environment: Environment,
) : Registry<DispatcherBulkheadConfig, DispatcherBulkhead> by instances {
    /**
     * Returns the bulkhead [name], created from the configuration [config] if the registry has none.
     *
     * @param name the name of the bulkhead.
     * @param config the name of the configuration; the default configuration by default.
     * @return the bulkhead.
     * @throws IllegalArgumentException if [name] is no valid instance name or [config] names no configuration.
     * @throws com.davils.kontinuity.core.registry.InstanceLimitException if the bulkhead is new and the registry is
     *   full.
     * @since 1.0.0
     */
    public fun dispatcherBulkhead(name: String, config: String = RegistrySettings.DEFAULT_CONFIG): DispatcherBulkhead =
        instances.getOrCreate(name, config)

    /**
     * Returns the bulkhead [name], created from [config] if the registry has none.
     *
     * @param name the name of the bulkhead.
     * @param config the configuration of a new bulkhead.
     * @return the bulkhead.
     * @throws IllegalArgumentException if [name] is no valid instance name.
     * @throws com.davils.kontinuity.core.registry.InstanceLimitException if the bulkhead is new and the registry is
     *   full.
     * @since 1.0.0
     */
    public fun dispatcherBulkhead(name: String, config: DispatcherBulkheadConfig): DispatcherBulkhead =
        instances.getOrCreate(name, config)

    /**
     * Returns the bulkhead [name], created from the configuration [base] changed by [configure] if the registry has
     * none; an existing bulkhead keeps its configuration.
     *
     * @param name the name of the bulkhead.
     * @param base the name of the configuration the new one starts from; the default configuration by default.
     * @param configure changes what differs from [base].
     * @return the bulkhead.
     * @throws IllegalArgumentException if [name] is no valid instance name or [base] names no configuration.
     * @throws com.davils.kore.core.validation.report.ValidationException if the configuration is invalid.
     * @throws com.davils.kontinuity.core.registry.InstanceLimitException if the bulkhead is new and the registry is
     *   full.
     * @since 1.0.0
     */
    public fun dispatcherBulkhead(
        name: String,
        base: String = RegistrySettings.DEFAULT_CONFIG,
        configure: DispatcherBulkheadConfigBuilder.() -> Unit,
    ): DispatcherBulkhead {
        val existing = find(name)
        if (existing != null) return existing
        val start = requireNotNull(settings.config(base)) {
            "The dispatcher bulkhead registry has no such configuration"
        }
        return instances.getOrCreate(name, dispatcherBulkheadConfig(start, configure))
    }

    /**
     * Shows the number of bulkheads.
     *
     * @return the registry as text.
     * @since 1.0.0
     */
    override fun toString(): String = "DispatcherBulkheadRegistry(${all.size} dispatcher bulkheads)"
}
