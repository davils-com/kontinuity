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

package com.davils.kontinuity.policy

import com.davils.kontinuity.cache.Cache
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.fallback.Fallback
import com.davils.kontinuity.core.through
import kotlinx.coroutines.flow.Flow

/**
 * Builds a policy: `resilience("checkout") { retry(retry); circuitBreaker(breaker); timeLimiter(limiter) }`.
 *
 * @param name the name of the policy, as [InstanceName] checks it.
 * @param environment the environment of the policy: its tracer, event bus and meter registry; [Environment.Default]
 *   by default.
 * @param configure declares the components and further stages.
 * @return the policy.
 * @throws IllegalArgumentException if [name] is no valid instance name.
 * @throws com.davils.kore.core.validation.report.ValidationException if the policy has no stage, declares a kind of
 *   component twice, holds an instance twice or takes more permits than its rate limiter allows.
 * @since 1.0.0
 */
public fun resilience(
    name: String,
    environment: Environment = Environment.Default,
    configure: PolicyBuilder.() -> Unit,
): Policy {
    val builder = PolicyBuilder(InstanceName.of(name), environment)
    builder.configure()
    return builder.build()
}

/**
 * Runs [block] through the policy and replaces its failure with the value of [fallback]; the fallback is the
 * outermost stage, so it also handles rejections and exhausted retries.
 *
 * @receiver the policy.
 * @param T the type of the result.
 * @param fallback the fallback.
 * @param block the protected call.
 * @return the result of [block] or of a handler of [fallback].
 * @throws Throwable a failure no handler of [fallback] selects, or the failure of a handler.
 * @since 1.0.0
 */
public suspend fun <T> Policy.execute(fallback: Fallback<T>, block: suspend () -> T): T =
    fallback.execute { execute(block) }

/**
 * Puts [cache] in front of this policy.
 *
 * @receiver the policy that protects every load.
 * @param K the type of the keys.
 * @param V the type of the values.
 * @param cache the cache.
 * @return the cached policy.
 * @since 1.0.0
 */
public fun <K : Any, V : Any> Policy.cached(cache: Cache<K, V>): CachedPolicy<K, V> = CachedPolicy(this, cache)

/**
 * Returns a flow that collects this flow through [policy]: the whole collection is one call; a retry collects the
 * flow again from the start.
 *
 * @receiver the flow to protect.
 * @param T the type of the elements.
 * @param policy the policy.
 * @return the protected flow.
 * @since 1.0.0
 */
public fun <T> Flow<T>.resilience(policy: Policy): Flow<T> = through(policy)
