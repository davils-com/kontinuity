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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Builds a validated dispatcher bulkhead configuration that starts from [base] and changes what [configure] sets.
 *
 * @param base the configuration every property starts from; [DispatcherBulkheadConfig.Default] by default.
 * @param configure changes what differs from [base].
 * @return the configuration.
 * @throws com.davils.kore.core.validation.report.ValidationException if a value is out of its bounds; the report
 *   names every problem.
 * @since 1.0.0
 */
public fun dispatcherBulkheadConfig(
    base: DispatcherBulkheadConfig = DispatcherBulkheadConfig.Default,
    configure: DispatcherBulkheadConfigBuilder.() -> Unit,
): DispatcherBulkheadConfig {
    val builder = DispatcherBulkheadConfigBuilder()
    builder.from(base)
    builder.configure()
    return builder.build()
}

/**
 * Creates a dispatcher bulkhead that runs its calls on [dispatcher]:
 * `dispatcherBulkhead("reports", Dispatchers.IO) { maxConcurrentCalls = 4 }`.
 *
 * @param name the name of the bulkhead, as [InstanceName] checks it.
 * @param dispatcher the dispatcher of the caller that runs the calls; it must support `limitedParallelism`, which
 *   `Dispatchers.Unconfined` does not.
 * @param environment the environment of the bulkhead; [Environment.Default] by default.
 * @param configure changes what differs from [DispatcherBulkheadConfig.Default].
 * @return the bulkhead.
 * @throws IllegalArgumentException if [name] is no valid instance name or [dispatcher] cannot limit its parallelism.
 * @throws com.davils.kore.core.validation.report.ValidationException if the configuration is invalid.
 * @since 1.0.0
 */
public fun dispatcherBulkhead(
    name: String,
    dispatcher: CoroutineDispatcher,
    environment: Environment = Environment.Default,
    configure: DispatcherBulkheadConfigBuilder.() -> Unit = {},
): DispatcherBulkhead = DispatcherBulkhead(
    name = InstanceName.of(name),
    config = dispatcherBulkheadConfig(configure = configure),
    dispatcher = dispatcher,
    environment = environment,
)

/**
 * Creates a dispatcher bulkhead from a configuration built before.
 *
 * @param name the name of the bulkhead, as [InstanceName] checks it.
 * @param dispatcher the dispatcher of the caller that runs the calls; it must support `limitedParallelism`.
 * @param config the configuration.
 * @param environment the environment of the bulkhead; [Environment.Default] by default.
 * @return the bulkhead.
 * @throws IllegalArgumentException if [name] is no valid instance name or [dispatcher] cannot limit its parallelism.
 * @since 1.0.0
 */
public fun dispatcherBulkhead(
    name: String,
    dispatcher: CoroutineDispatcher,
    config: DispatcherBulkheadConfig,
    environment: Environment = Environment.Default,
): DispatcherBulkhead = DispatcherBulkhead(
    name = InstanceName.of(name),
    config = config,
    dispatcher = dispatcher,
    environment = environment,
)

/**
 * Creates a registry of dispatcher bulkheads that run their calls on [dispatcher]:
 * `dispatcherBulkheadRegistry(Dispatchers.IO) { defaults { ... }; config("narrow") { ... } }`.
 *
 * @param dispatcher the dispatcher of the caller that runs the calls of every bulkhead of the registry.
 * @param environment the environment of every bulkhead of the registry; [Environment.Default] by default.
 * @param configure declares the limits and the configurations.
 * @return the registry.
 * @throws com.davils.kore.core.validation.report.ValidationException if a limit or a configuration is invalid; the
 *   report names every problem.
 * @since 1.0.0
 */
public fun dispatcherBulkheadRegistry(
    dispatcher: CoroutineDispatcher,
    environment: Environment = Environment.Default,
    configure: DispatcherBulkheadRegistryBuilder.() -> Unit = {},
): DispatcherBulkheadRegistry {
    val builder = DispatcherBulkheadRegistryBuilder()
    builder.configure()
    val instances = Instances(
        kind = ResilienceKind.DispatcherBulkhead,
        settings = builder.build(),
        environment = environment,
    ) { name, config ->
        DispatcherBulkhead(name = name, config = config, dispatcher = dispatcher, environment = environment)
    }
    return DispatcherBulkheadRegistry(instances = instances, dispatcher = dispatcher, environment = environment)
}

/**
 * Returns a flow that collects this flow through [bulkhead]: the whole collection is one call, which runs on the
 * dispatcher of the bulkhead.
 *
 * @receiver the flow to protect.
 * @param T the type of the elements.
 * @param bulkhead the dispatcher bulkhead.
 * @return the protected flow, which ends with a [BulkheadFullException] if the bulkhead refuses the collection.
 * @since 1.0.0
 */
public fun <T> Flow<T>.dispatcherBulkhead(bulkhead: DispatcherBulkhead): Flow<T> = through(bulkhead)
