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

package com.davils.kontinuity.retry

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.registry.Instances
import com.davils.kontinuity.core.through
import com.davils.kore.core.annotation.DavilsInternal
import kotlinx.coroutines.flow.Flow

/**
 * Builds a validated retry configuration that starts from [base] and changes what [configure] sets.
 *
 * @param base the configuration every property starts from; [RetryConfig.Default] by default.
 * @param configure changes what differs from [base].
 * @return the configuration.
 * @throws com.davils.kore.core.validation.report.ValidationException if a value is out of its bounds; the report
 *   names every problem.
 * @since 1.0.0
 */
public fun retryConfig(
    base: RetryConfig = RetryConfig.Default,
    configure: RetryConfigBuilder.() -> Unit,
): RetryConfig {
    val builder = RetryConfigBuilder()
    builder.from(base)
    builder.configure()
    return builder.build()
}

/**
 * Creates a retry: `retry("payments") { maxAttempts = 5 }`.
 *
 * @param name the name of the retry, as [InstanceName] checks it.
 * @param environment the environment of the retry; [Environment.Default] by default.
 * @param configure changes what differs from [RetryConfig.Default].
 * @return the retry.
 * @throws IllegalArgumentException if [name] is no valid instance name.
 * @throws com.davils.kore.core.validation.report.ValidationException if the configuration is invalid.
 * @since 1.0.0
 */
public fun retry(
    name: String,
    environment: Environment = Environment.Default,
    configure: RetryConfigBuilder.() -> Unit = {},
): Retry = Retry(InstanceName.of(name), retryConfig(configure = configure), environment)

/**
 * Creates a retry from a configuration built before.
 *
 * @param name the name of the retry, as [InstanceName] checks it.
 * @param config the configuration.
 * @param environment the environment of the retry; [Environment.Default] by default.
 * @return the retry.
 * @throws IllegalArgumentException if [name] is no valid instance name.
 * @since 1.0.0
 */
public fun retry(name: String, config: RetryConfig, environment: Environment = Environment.Default): Retry =
    Retry(InstanceName.of(name), config, environment)

/**
 * Creates a registry of retries: `retryRegistry { defaults { ... }; config("patient") { ... } }`.
 *
 * @param environment the environment of every retry of the registry; [Environment.Default] by default.
 * @param configure declares the limits and the configurations.
 * @return the registry.
 * @throws com.davils.kore.core.validation.report.ValidationException if a limit or a configuration is invalid; the
 *   report names every problem.
 * @since 1.0.0
 */
public fun retryRegistry(
    environment: Environment = Environment.Default,
    configure: RetryRegistryBuilder.() -> Unit = {},
): RetryRegistry {
    val builder = RetryRegistryBuilder()
    builder.configure()
    val instances = Instances(
        kind = ResilienceKind.Retry,
        settings = builder.build(),
        environment = environment,
    ) { name, config -> Retry(name, config, environment) }
    return RetryRegistry(instances, environment)
}

/**
 * Creates a retry budget that any number of retries may share: `retryBudget { ratio = 0.1 }`.
 *
 * @param environment the environment whose time the budget counts in; [Environment.Default] by default.
 * @param configure changes what differs from the defaults of Finagle and gRPC.
 * @return the budget.
 * @throws com.davils.kore.core.validation.report.ValidationException if a value is out of its bounds; the report
 *   names every problem.
 * @since 1.0.0
 */
public fun retryBudget(
    environment: Environment = Environment.Default,
    configure: RetryBudgetBuilder.() -> Unit = {},
): RetryBudget {
    val builder = RetryBudgetBuilder(environment.timeSource)
    builder.configure()
    return builder.build()
}

/**
 * Returns a flow that collects this flow through [retry]: a collection that fails is started anew, as the retry
 * decides, by collecting this flow again from its start.
 *
 * Every element of a failed collection was emitted downstream already, so a collection that is repeated emits its
 * elements again; collect a flow whose elements may repeat, or drop the duplicates downstream. A cold flow is the
 * usual upstream; a hot flow is subscribed anew and misses what it emitted in between.
 *
 * @receiver the flow to protect.
 * @param T the type of the elements.
 * @param retry the retry.
 * @return the protected flow.
 * @since 1.0.0
 */
public fun <T> Flow<T>.retry(retry: Retry): Flow<T> = through(retry)
