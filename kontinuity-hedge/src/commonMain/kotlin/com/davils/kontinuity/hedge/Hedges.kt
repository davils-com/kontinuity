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

package com.davils.kontinuity.hedge

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.registry.Instances
import com.davils.kore.core.annotation.DavilsInternal
import kotlin.time.Duration

/**
 * Builds a validated hedge configuration that starts from [base] and changes what [configure] sets.
 *
 * @param base the configuration every property starts from; [HedgeConfig.Default] by default.
 * @param configure changes what differs from [base].
 * @return the configuration.
 * @throws com.davils.kore.core.validation.report.ValidationException if a value is out of its bounds; the report
 *   names every problem.
 * @since 1.0.0
 */
public fun hedgeConfig(
    base: HedgeConfig = HedgeConfig.Default,
    configure: HedgeConfigBuilder.() -> Unit,
): HedgeConfig {
    val builder = HedgeConfigBuilder()
    builder.from(base)
    builder.configure()
    return builder.build()
}

/**
 * Creates a hedge: `hedge("search") { delay = fixedDelay(50.milliseconds); maxHedgedAttempts = 2 }`.
 *
 * @param name the name of the hedge, as [InstanceName] checks it.
 * @param environment the environment of the hedge; [Environment.Default] by default.
 * @param configure changes what differs from [HedgeConfig.Default].
 * @return the hedge.
 * @throws IllegalArgumentException if [name] is no valid instance name.
 * @throws com.davils.kore.core.validation.report.ValidationException if the configuration is invalid.
 * @since 1.0.0
 */
public fun hedge(
    name: String,
    environment: Environment = Environment.Default,
    configure: HedgeConfigBuilder.() -> Unit = {},
): Hedge = Hedge(InstanceName.of(name), hedgeConfig(configure = configure), environment)

/**
 * Creates a hedge from a configuration built before.
 *
 * @param name the name of the hedge, as [InstanceName] checks it.
 * @param config the configuration.
 * @param environment the environment of the hedge; [Environment.Default] by default.
 * @return the hedge.
 * @throws IllegalArgumentException if [name] is no valid instance name.
 * @since 1.0.0
 */
public fun hedge(name: String, config: HedgeConfig, environment: Environment = Environment.Default): Hedge =
    Hedge(InstanceName.of(name), config, environment)

/**
 * Creates a registry of hedges: `hedgeRegistry { defaults { ... }; config("tail") { ... } }`.
 *
 * @param environment the environment of every hedge of the registry; [Environment.Default] by default.
 * @param configure declares the limits and the configurations.
 * @return the registry.
 * @throws com.davils.kore.core.validation.report.ValidationException if a limit or a configuration is invalid; the
 *   report names every problem.
 * @since 1.0.0
 */
public fun hedgeRegistry(
    environment: Environment = Environment.Default,
    configure: HedgeRegistryBuilder.() -> Unit = {},
): HedgeRegistry {
    val builder = HedgeRegistryBuilder()
    builder.configure()
    val instances = Instances(
        kind = ResilienceKind.Hedge,
        settings = builder.build(),
        environment = environment,
    ) { name, config -> Hedge(name, config, environment) }
    return HedgeRegistry(instances, environment)
}

/**
 * Returns a delay that waits [duration] before every hedged copy.
 *
 * @param duration the delay, positive and at most [HedgeDelay.HARD_DELAY].
 * @return the delay.
 * @throws com.davils.kore.core.validation.report.ValidationException if [duration] is out of its bounds.
 * @since 1.0.0
 */
public fun fixedDelay(duration: Duration): HedgeDelay = HedgeDelay.Fixed(duration)

/**
 * Returns a delay that waits the [percentile] of the latencies of the last [window] primaries that succeeded, and
 * [fallback] while fewer than [minimumSamples] latencies are known.
 *
 * @param percentile the percentile as a fraction, above 0 and at most 1; 0.95 by default.
 * @param minimumSamples how many latencies the percentile needs, from 1 to [window]; 100 by default.
 * @param window how many latencies the hedge keeps, from 1 to [HedgeDelay.HARD_WINDOW]; 1,000 by default.
 * @param fallback the delay while too few latencies are known, positive and at most [HedgeDelay.HARD_DELAY]; 100
 *   milliseconds by default.
 * @return the delay.
 * @throws com.davils.kore.core.validation.report.ValidationException if a value is out of its bounds; the report
 *   names every problem.
 * @since 1.0.0
 */
public fun percentileDelay(
    percentile: Double = HedgeDelay.DEFAULT_PERCENTILE,
    minimumSamples: Int = HedgeDelay.DEFAULT_MINIMUM_SAMPLES,
    window: Int = HedgeDelay.DEFAULT_WINDOW,
    fallback: Duration = HedgeDelay.DEFAULT_DELAY,
): HedgeDelay = HedgeDelay.Percentile(
    percentile = percentile,
    minimumSamples = minimumSamples,
    window = window,
    fallback = fallback,
)
