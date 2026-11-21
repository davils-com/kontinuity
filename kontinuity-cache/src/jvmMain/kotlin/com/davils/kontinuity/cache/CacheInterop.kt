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

import com.davils.kontinuity.cache.internal.loadBlocking

/**
 * Returns the live value of [key] or loads it with the blocking [loader], for Java and blocking code, with the same
 * single-flight loading as [Cache.getOrLoad].
 *
 * Concurrent misses of one key share one load, whether their callers block or suspend: the first caller runs
 * [loader] on its own thread and every other caller waits for it, a blocking one on a `CompletableFuture` of its
 * own. A waiter receives the value or a [CacheLoadException] of its own that wraps the failure, the loading caller
 * the failure of [loader] itself. A load whose thread is interrupted ends without a value and one of the waiters
 * loads again; an interrupted waiter stops waiting with an `InterruptedException` and keeps its interrupt flag. The
 * wait for a load is as long as the load: bound it by bounding [loader]. Fatal errors pass through.
 *
 * @receiver the cache.
 * @param K the type of the keys.
 * @param V the type of the values.
 * @param key the key.
 * @param loader loads the value of a missing key; it runs outside the lock of the cache.
 * @return the value.
 * @throws CacheLoadException if this call waited for the load of another caller and that load failed.
 * @throws IllegalArgumentException if the weigher fails for the loaded value or returns a negative weight.
 * @throws InterruptedException if the thread is interrupted while it waits.
 * @throws Throwable what [loader] throws, if this call ran the load.
 * @since 1.0.0
 */
public fun <K : Any, V : Any> Cache<K, V>.getOrLoadBlocking(key: K, loader: (K) -> V): V =
    loads.loadBlocking(key, loader)
