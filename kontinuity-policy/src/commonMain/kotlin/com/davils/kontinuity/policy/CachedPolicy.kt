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

/**
 * A [Policy] behind a [Cache]: a call first looks up its key, and only a miss runs the loader through every stage of
 * the policy, once for all concurrent callers of the same key.
 *
 * Create one with `policy.cached(cache)`. A failed or refused load is not cached; the cache's own failure handling,
 * such as serving a stale value, applies. It is safe to use from many coroutines at once.
 *
 * @param K the type of the keys.
 * @param V the type of the values.
 * @property policy the policy that protects every load.
 * @property cache the cache in front of the policy.
 * @since 1.0.0
 */
public class CachedPolicy<K : Any, V : Any> internal constructor(
    public val policy: Policy,
    public val cache: Cache<K, V>,
) {
    /**
     * Returns the cached value of [key], or loads it with [loader] through the policy and caches it.
     *
     * @param key the key.
     * @param loader loads the value of a key; it runs through every stage of the policy.
     * @return the value.
     * @throws com.davils.kontinuity.core.CallRejectedException if a stage refuses the load of this caller.
     * @throws Throwable what the load throws, as the cache reports it.
     * @since 1.0.0
     */
    public suspend fun execute(key: K, loader: suspend (K) -> V): V =
        cache.getOrLoad(key) { missing -> policy.execute { loader(missing) } }

    /**
     * Removes the cached value of [key], so the next call loads it again.
     *
     * @param key the key.
     * @return the removed value, or null if none was cached.
     * @since 1.0.0
     */
    public fun invalidate(key: K): V? = cache.remove(key)

    /**
     * Shows the policy and the cache.
     *
     * @return the cached policy as text.
     * @since 1.0.0
     */
    override fun toString(): String = "CachedPolicy(${policy.name}, ${cache.name})"
}
