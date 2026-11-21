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

package com.davils.kontinuity.circuitbreaker

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.registry.Instances
import com.davils.kontinuity.core.through
import com.davils.kore.core.annotation.DavilsInternal
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Builds a validated circuit breaker configuration that starts from [base] and changes what [configure] sets.
 *
 * @param base the configuration every property starts from; [CircuitBreakerConfig.Default] by default.
 * @param configure changes what differs from [base].
 * @return the configuration.
 * @throws com.davils.kore.core.validation.report.ValidationException if a value is out of its bounds; the report
 *   names every problem.
 * @since 1.0.0
 */
public fun circuitBreakerConfig(
    base: CircuitBreakerConfig = CircuitBreakerConfig.Default,
    configure: CircuitBreakerConfigBuilder.() -> Unit,
): CircuitBreakerConfig {
    val builder = CircuitBreakerConfigBuilder()
    builder.from(base)
    builder.configure()
    return builder.build()
}

/**
 * Creates a circuit breaker: `circuitBreaker("payments") { failureRateThreshold = 30.0 }`.
 *
 * @param name the name of the breaker, as [InstanceName] checks it.
 * @param environment the environment of the breaker; [Environment.Default] by default.
 * @param configure changes what differs from [CircuitBreakerConfig.Default].
 * @return the breaker.
 * @throws IllegalArgumentException if [name] is no valid instance name, or if the automatic transition is enabled
 *   and the environment has no scope.
 * @throws com.davils.kore.core.validation.report.ValidationException if the configuration is invalid.
 * @since 1.0.0
 */
public fun circuitBreaker(
    name: String,
    environment: Environment = Environment.Default,
    configure: CircuitBreakerConfigBuilder.() -> Unit = {},
): CircuitBreaker = CircuitBreaker(InstanceName.of(name), circuitBreakerConfig(configure = configure), environment)

/**
 * Creates a circuit breaker from a configuration built before.
 *
 * @param name the name of the breaker, as [InstanceName] checks it.
 * @param config the configuration.
 * @param environment the environment of the breaker; [Environment.Default] by default.
 * @return the breaker.
 * @throws IllegalArgumentException if [name] is no valid instance name, or if the automatic transition is enabled
 *   and the environment has no scope.
 * @since 1.0.0
 */
public fun circuitBreaker(
    name: String,
    config: CircuitBreakerConfig,
    environment: Environment = Environment.Default,
): CircuitBreaker = CircuitBreaker(InstanceName.of(name), config, environment)

/**
 * Creates a registry of circuit breakers: `circuitBreakerRegistry { defaults { ... }; config("strict") { ... } }`.
 *
 * @param environment the environment of every breaker of the registry; [Environment.Default] by default.
 * @param configure declares the limits and the configurations.
 * @return the registry.
 * @throws com.davils.kore.core.validation.report.ValidationException if a limit or a configuration is invalid; the
 *   report names every problem.
 * @since 1.0.0
 */
public fun circuitBreakerRegistry(
    environment: Environment = Environment.Default,
    configure: CircuitBreakerRegistryBuilder.() -> Unit = {},
): CircuitBreakerRegistry {
    val builder = CircuitBreakerRegistryBuilder()
    builder.configure()
    val instances = Instances(
        kind = ResilienceKind.CircuitBreaker,
        settings = builder.build(),
        environment = environment,
    ) { name, config -> CircuitBreaker(name, config, environment) }
    return CircuitBreakerRegistry(instances, environment)
}

/**
 * Returns a window of the outcomes of the last [size] calls.
 *
 * @param size how many calls the window holds, from 1 to [SlidingWindow.HARD_SIZE].
 * @return the window.
 * @throws com.davils.kore.core.validation.report.ValidationException if [size] is out of its bounds.
 * @since 1.0.0
 */
public fun countBased(size: Int): SlidingWindow = SlidingWindow.CountBased(size)

/**
 * Returns a window of the outcomes of the calls of the last [length], kept in [buckets] parts.
 *
 * @param length how far back the window reaches, positive and at most [SlidingWindow.HARD_LENGTH].
 * @param buckets how many parts the window keeps; one per second of [length] by default, from 1 to
 *   [SlidingWindow.HARD_BUCKETS].
 * @return the window.
 * @throws com.davils.kore.core.validation.report.ValidationException if a value is out of its bounds.
 * @since 1.0.0
 */
public fun timeBased(length: Duration, buckets: Int = defaultBuckets(length)): SlidingWindow =
    SlidingWindow.TimeBased(length, buckets)

/**
 * Returns a flow that collects this flow through [breaker]: the collection asks for a permit when it starts and
 * records its outcome when it ends.
 *
 * @receiver the flow to protect.
 * @param T the type of the elements.
 * @param breaker the breaker.
 * @return the protected flow.
 * @since 1.0.0
 */
public fun <T> Flow<T>.circuitBreaker(breaker: CircuitBreaker): Flow<T> = through(breaker)

private fun defaultBuckets(length: Duration): Int {
    val seconds = length.toLong(DurationUnit.SECONDS)
    return seconds.coerceIn(minimumValue = 1L, maximumValue = SlidingWindow.HARD_BUCKETS.toLong()).toInt()
}
