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

package com.davils.kontinuity.timelimiter

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.registry.Instances
import com.davils.kontinuity.core.through
import com.davils.kore.core.annotation.DavilsInternal
import kotlinx.coroutines.flow.Flow

/**
 * Builds a validated time limiter configuration that starts from [base] and changes what [configure] sets.
 *
 * @param base the configuration every property starts from; [TimeLimiterConfig.Default] by default.
 * @param configure changes what differs from [base].
 * @return the configuration.
 * @throws com.davils.kore.core.validation.report.ValidationException if a value is out of its bounds; the report
 *   names every problem.
 * @since 1.0.0
 */
public fun timeLimiterConfig(
    base: TimeLimiterConfig = TimeLimiterConfig.Default,
    configure: TimeLimiterConfigBuilder.() -> Unit,
): TimeLimiterConfig {
    val builder = TimeLimiterConfigBuilder()
    builder.from(base)
    builder.configure()
    return builder.build()
}

/**
 * Creates a time limiter: `timeLimiter("payments") { timeout = 2.seconds }`.
 *
 * @param name the name of the limiter, as [InstanceName] checks it.
 * @param environment the environment of the limiter; [Environment.Default] by default, which has no scope and so
 *   allows only the hard strategy.
 * @param configure changes what differs from [TimeLimiterConfig.Default].
 * @return the limiter.
 * @throws IllegalArgumentException if [name] is no valid instance name, or if the strategy is soft and the
 *   environment has no scope.
 * @throws com.davils.kore.core.validation.report.ValidationException if the configuration is invalid.
 * @since 1.0.0
 */
public fun timeLimiter(
    name: String,
    environment: Environment = Environment.Default,
    configure: TimeLimiterConfigBuilder.() -> Unit = {},
): TimeLimiter = TimeLimiter(InstanceName.of(name), timeLimiterConfig(configure = configure), environment)

/**
 * Creates a time limiter from a configuration built before.
 *
 * @param name the name of the limiter, as [InstanceName] checks it.
 * @param config the configuration.
 * @param environment the environment of the limiter; [Environment.Default] by default.
 * @return the limiter.
 * @throws IllegalArgumentException if [name] is no valid instance name, or if the strategy is soft and the
 *   environment has no scope.
 * @since 1.0.0
 */
public fun timeLimiter(
    name: String,
    config: TimeLimiterConfig,
    environment: Environment = Environment.Default,
): TimeLimiter = TimeLimiter(InstanceName.of(name), config, environment)

/**
 * Creates a registry of time limiters: `timeLimiterRegistry { defaults { ... }; config("slow") { ... } }`.
 *
 * @param environment the environment of every limiter of the registry; [Environment.Default] by default.
 * @param configure declares the limits and the configurations.
 * @return the registry.
 * @throws com.davils.kore.core.validation.report.ValidationException if a limit or a configuration is invalid; the
 *   report names every problem.
 * @since 1.0.0
 */
public fun timeLimiterRegistry(
    environment: Environment = Environment.Default,
    configure: TimeLimiterRegistryBuilder.() -> Unit = {},
): TimeLimiterRegistry {
    val builder = TimeLimiterRegistryBuilder()
    builder.configure()
    val instances = Instances(
        kind = ResilienceKind.TimeLimiter,
        settings = builder.build(),
        environment = environment,
    ) { name, config -> TimeLimiter(name, config, environment) }
    return TimeLimiterRegistry(instances, environment)
}

/**
 * Returns a flow whose whole collection runs within the timeout of [limiter]: a collection that has not ended when
 * the timeout runs out ends with a `CallTimeoutException`, however many elements it delivered.
 *
 * With a soft limiter the upstream is collected in the detached call on the scope of the environment and its
 * elements reach the collector through a rendezvous channel.
 *
 * @receiver the flow to protect.
 * @param T the type of the elements.
 * @param limiter the limiter.
 * @return the protected flow.
 * @since 1.0.0
 */
public fun <T> Flow<T>.timeLimiter(limiter: TimeLimiter): Flow<T> = through(limiter)
