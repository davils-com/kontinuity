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

/**
 * The named bulkheads of an application and the configurations they are created from.
 *
 * Create one with `bulkheadRegistry { defaults { maxConcurrentCalls = 10 }; config("narrow") { ... } }`. A registry
 * creates a bulkhead at most once per name, hands every bulkhead its [environment] and holds at most the instances
 * its limits allow. It is safe to use from many coroutines and threads at once.
 *
 * @param instances the instances the registry delegates to.
 * @property environment the environment of every bulkhead of the registry.
 * @since 1.0.0
 */
public class BulkheadRegistry internal constructor(
    private val instances: Instances<BulkheadConfig, Bulkhead>,
    public val environment: Environment,
) : Registry<BulkheadConfig, Bulkhead> by instances {
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
    public fun bulkhead(name: String, config: String = RegistrySettings.DEFAULT_CONFIG): Bulkhead =
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
    public fun bulkhead(name: String, config: BulkheadConfig): Bulkhead = instances.getOrCreate(name, config)

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
    public fun bulkhead(
        name: String,
        base: String = RegistrySettings.DEFAULT_CONFIG,
        configure: BulkheadConfigBuilder.() -> Unit,
    ): Bulkhead {
        val existing = find(name)
        if (existing != null) return existing
        val start = requireNotNull(settings.config(base)) { "The bulkhead registry has no such configuration" }
        return instances.getOrCreate(name, bulkheadConfig(start, configure))
    }

    /**
     * Shows the number of bulkheads.
     *
     * @return the registry as text.
     * @since 1.0.0
     */
    override fun toString(): String = "BulkheadRegistry(${all.size} bulkheads)"
}
