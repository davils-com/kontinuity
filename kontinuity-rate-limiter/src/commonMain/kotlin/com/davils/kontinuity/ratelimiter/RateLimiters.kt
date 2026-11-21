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

package com.davils.kontinuity.ratelimiter

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.registry.Instances
import com.davils.kore.core.annotation.DavilsInternal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Builds a validated rate limiter configuration that starts from [base] and changes what [configure] sets.
 *
 * @param base the configuration every property starts from; [RateLimiterConfig.Default] by default.
 * @param configure changes what differs from [base].
 * @return the configuration.
 * @throws com.davils.kore.core.validation.report.ValidationException if a value is out of its bounds; the report
 *   names every problem.
 * @since 1.0.0
 */
public fun rateLimiterConfig(
    base: RateLimiterConfig = RateLimiterConfig.Default,
    configure: RateLimiterConfigBuilder.() -> Unit,
): RateLimiterConfig {
    val builder = RateLimiterConfigBuilder()
    builder.from(base)
    builder.configure()
    return builder.build()
}

/**
 * Creates a rate limiter: `rateLimiter("search") { limitForPeriod = 100; limitRefreshPeriod = 1.seconds }`.
 *
 * @param name the name of the limiter, as [InstanceName] checks it.
 * @param environment the environment of the limiter; [Environment.Default] by default.
 * @param configure changes what differs from [RateLimiterConfig.Default].
 * @return the limiter.
 * @throws IllegalArgumentException if [name] is no valid instance name.
 * @throws com.davils.kore.core.validation.report.ValidationException if the configuration is invalid.
 * @since 1.0.0
 */
public fun rateLimiter(
    name: String,
    environment: Environment = Environment.Default,
    configure: RateLimiterConfigBuilder.() -> Unit = {},
): RateLimiter = RateLimiter(InstanceName.of(name), rateLimiterConfig(configure = configure), environment)

/**
 * Creates a rate limiter from a configuration built before.
 *
 * @param name the name of the limiter, as [InstanceName] checks it.
 * @param config the configuration.
 * @param environment the environment of the limiter; [Environment.Default] by default.
 * @return the limiter.
 * @throws IllegalArgumentException if [name] is no valid instance name.
 * @since 1.0.0
 */
public fun rateLimiter(
    name: String,
    config: RateLimiterConfig,
    environment: Environment = Environment.Default,
): RateLimiter = RateLimiter(InstanceName.of(name), config, environment)

/**
 * Creates a registry of rate limiters: `rateLimiterRegistry { defaults { ... }; config("strict") { ... } }`.
 *
 * @param environment the environment of every limiter of the registry; [Environment.Default] by default.
 * @param configure declares the limits and the configurations.
 * @return the registry.
 * @throws com.davils.kore.core.validation.report.ValidationException if a limit or a configuration is invalid; the
 *   report names every problem.
 * @since 1.0.0
 */
public fun rateLimiterRegistry(
    environment: Environment = Environment.Default,
    configure: RateLimiterRegistryBuilder.() -> Unit = {},
): RateLimiterRegistry {
    val builder = RateLimiterRegistryBuilder()
    builder.configure()
    val instances = Instances(
        kind = ResilienceKind.RateLimiter,
        settings = builder.build(),
        environment = environment,
    ) { name, config -> RateLimiter(name, config, environment) }
    return RateLimiterRegistry(instances, environment)
}

/**
 * Returns a token bucket of [burst] permits for [RateLimiterConfigBuilder.algorithm].
 *
 * @param burst how many permits the bucket holds, from 1 to [RateLimiterAlgorithm.HARD_BURST] and at least the limit
 *   per period of the configuration.
 * @return the algorithm.
 * @throws com.davils.kore.core.validation.report.ValidationException if [burst] is out of its bounds.
 * @since 1.0.0
 */
public fun tokenBucket(burst: Int): RateLimiterAlgorithm = RateLimiterAlgorithm.TokenBucket(burst)

/**
 * Returns a flow that takes [permits] permits of [limiter] before it emits each element of this flow, so that the
 * elements pass at the rate of the limiter.
 *
 * The permits are taken in the collector's coroutine before the element is handed on; an element whose permits are
 * refused ends the flow with a [RequestNotPermittedException], and the cancellation of the collector while it waits
 * gives the reservation back. To limit the collection as a whole instead, with one permit when it starts, use
 * `flow.through(limiter)` of kontinuity-core.
 *
 * @receiver the flow to limit.
 * @param T the type of the elements.
 * @param limiter the limiter.
 * @param permits how many permits each element takes, from 1 to the limit per period; one by default.
 * @return the limited flow.
 * @since 1.0.0
 */
public fun <T> Flow<T>.rateLimiter(limiter: RateLimiter, permits: Int = 1): Flow<T> = onEach {
    limiter.acquire(permits)
}
