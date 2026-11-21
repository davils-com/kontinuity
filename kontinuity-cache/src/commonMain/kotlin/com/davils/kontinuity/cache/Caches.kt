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

package com.davils.kontinuity.cache

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName

/**
 * Builds a validated cache configuration from the defaults and what [configure] sets, to share between caches.
 *
 * @param K the type of the keys.
 * @param V the type of the values.
 * @param configure changes what differs from the defaults.
 * @return the configuration.
 * @throws com.davils.kore.core.validation.report.ValidationException if a value is out of its bounds; the report
 *   names every problem.
 * @since 1.0.0
 */
public fun <K : Any, V : Any> cacheConfig(configure: CacheBuilder<K, V>.() -> Unit): CacheConfig<K, V> {
    val builder = CacheBuilder<K, V>()
    builder.configure()
    return builder.build()
}

/**
 * Creates a cache: `cache<String, User>("users") { maximumSize = 10_000; expireAfterWrite = 5.minutes }`.
 *
 * Caches have no registry: an application keeps the caches it creates. A cache with a reaper runs it on the scope of
 * [environment] until that scope ends.
 *
 * @param K the type of the keys.
 * @param V the type of the values.
 * @param name the name of the cache, as [InstanceName] checks it.
 * @param environment the environment of the cache; [Environment.Default] by default.
 * @param configure changes what differs from the defaults.
 * @return the cache.
 * @throws IllegalArgumentException if [name] is no valid instance name, or if the configuration has a reaper and
 *   the environment has no scope.
 * @throws com.davils.kore.core.validation.report.ValidationException if the configuration is invalid.
 * @since 1.0.0
 */
public fun <K : Any, V : Any> cache(
    name: String,
    environment: Environment = Environment.Default,
    configure: CacheBuilder<K, V>.() -> Unit = {},
): Cache<K, V> = Cache(InstanceName.of(name), cacheConfig(configure), environment)

/**
 * Creates a cache from a configuration built before.
 *
 * @param K the type of the keys.
 * @param V the type of the values.
 * @param name the name of the cache, as [InstanceName] checks it.
 * @param config the configuration.
 * @param environment the environment of the cache; [Environment.Default] by default.
 * @return the cache.
 * @throws IllegalArgumentException if [name] is no valid instance name, or if the configuration has a reaper and
 *   the environment has no scope.
 * @since 1.0.0
 */
public fun <K : Any, V : Any> cache(
    name: String,
    config: CacheConfig<K, V>,
    environment: Environment = Environment.Default,
): Cache<K, V> = Cache(InstanceName.of(name), config, environment)
