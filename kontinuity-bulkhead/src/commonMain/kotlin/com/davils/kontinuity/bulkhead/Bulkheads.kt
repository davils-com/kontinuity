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
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.registry.Instances
import com.davils.kontinuity.core.through
import com.davils.kore.core.annotation.DavilsInternal
import kotlinx.coroutines.flow.Flow

/**
 * Builds a validated bulkhead configuration that starts from [base] and changes what [configure] sets.
 *
 * @param base the configuration every property starts from; [BulkheadConfig.Default] by default.
 * @param configure changes what differs from [base].
 * @return the configuration.
 * @throws com.davils.kore.core.validation.report.ValidationException if a value is out of its bounds; the report
 *   names every problem.
 * @since 1.0.0
 */
public fun bulkheadConfig(
    base: BulkheadConfig = BulkheadConfig.Default,
    configure: BulkheadConfigBuilder.() -> Unit,
): BulkheadConfig {
    val builder = BulkheadConfigBuilder()
    builder.from(base)
    builder.configure()
    return builder.build()
}

/**
 * Creates a bulkhead: `bulkhead("payments") { maxConcurrentCalls = 10 }`.
 *
 * @param name the name of the bulkhead, as [InstanceName] checks it.
 * @param environment the environment of the bulkhead; [Environment.Default] by default.
 * @param configure changes what differs from [BulkheadConfig.Default].
 * @return the bulkhead.
 * @throws IllegalArgumentException if [name] is no valid instance name.
 * @throws com.davils.kore.core.validation.report.ValidationException if the configuration is invalid.
 * @since 1.0.0
 */
public fun bulkhead(
    name: String,
    environment: Environment = Environment.Default,
    configure: BulkheadConfigBuilder.() -> Unit = {},
): Bulkhead = Bulkhead(InstanceName.of(name), bulkheadConfig(configure = configure), environment)

/**
 * Creates a bulkhead from a configuration built before.
 *
 * @param name the name of the bulkhead, as [InstanceName] checks it.
 * @param config the configuration.
 * @param environment the environment of the bulkhead; [Environment.Default] by default.
 * @return the bulkhead.
 * @throws IllegalArgumentException if [name] is no valid instance name.
 * @since 1.0.0
 */
public fun bulkhead(name: String, config: BulkheadConfig, environment: Environment = Environment.Default): Bulkhead =
    Bulkhead(InstanceName.of(name), config, environment)

/**
 * Creates a registry of bulkheads: `bulkheadRegistry { defaults { ... }; config("narrow") { ... } }`.
 *
 * @param environment the environment of every bulkhead of the registry; [Environment.Default] by default.
 * @param configure declares the limits and the configurations.
 * @return the registry.
 * @throws com.davils.kore.core.validation.report.ValidationException if a limit or a configuration is invalid; the
 *   report names every problem.
 * @since 1.0.0
 */
public fun bulkheadRegistry(
    environment: Environment = Environment.Default,
    configure: BulkheadRegistryBuilder.() -> Unit = {},
): BulkheadRegistry {
    val builder = BulkheadRegistryBuilder()
    builder.configure()
    val instances = Instances(
        kind = ResilienceKind.Bulkhead,
        settings = builder.build(),
        environment = environment,
    ) { name, config -> Bulkhead(name, config, environment) }
    return BulkheadRegistry(instances, environment)
}

/**
 * Returns a flow that collects this flow through [bulkhead]: the whole collection holds one permit, taken when it
 * starts and given back when it ends.
 *
 * @receiver the flow to protect.
 * @param T the type of the elements.
 * @param bulkhead the bulkhead.
 * @return the protected flow, which ends with a [BulkheadFullException] if the bulkhead refuses the collection.
 * @since 1.0.0
 */
public fun <T> Flow<T>.bulkhead(bulkhead: Bulkhead): Flow<T> = through(bulkhead)
