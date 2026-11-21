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

package com.davils.kontinuity.cache.internal

import com.davils.kore.core.failure.contained
import java.util.concurrent.CompletableFuture

internal fun <K : Any, V : Any> Loads<K, V>.loadBlocking(key: K, loader: (K) -> V): V {
    var value = attemptBlocking(key = key, loader = loader, isFirst = true)
    while (value == null) value = attemptBlocking(key = key, loader = loader, isFirst = false)
    return value
}

private fun <K : Any, V : Any> Loads<K, V>.attemptBlocking(key: K, loader: (K) -> V, isFirst: Boolean): V? =
    when (val lookup = lookup(key, isFirst)) {
        is Lookup.Hit -> lookup.value
        is Lookup.Lead -> lead(key, lookup.flight) { contained { loader(key) } }
        is Lookup.Join -> joined(key, lookup.flight.awaitBlocking())
    }

private fun <V : Any> Flight<V>.awaitBlocking(): Settled<V> {
    val future = CompletableFuture<Settled<V>>()
    whenSettled { settled -> future.complete(settled) }
    return contained { future.get() }.getOrThrow()
}
