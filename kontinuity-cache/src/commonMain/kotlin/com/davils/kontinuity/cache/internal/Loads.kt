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

import com.davils.kontinuity.cache.CacheLoadException
import com.davils.kontinuity.core.InstanceName
import com.davils.kore.core.failure.attempted
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

internal class Loads<K : Any, V : Any>(
    private val name: InstanceName,
    private val store: Store<K, V>,
    private val sizer: Sizer<K, V>,
    private val reporter: Reporter<K>,
    internal val timeSource: TimeSource.WithComparableMarks,
    private val isRecordingKeys: Boolean,
    private val isServingStale: Boolean,
) {
    suspend fun load(key: K, loader: suspend (K) -> V): V {
        var value = attempt(key = key, loader = loader, isFirst = true)
        while (value == null) value = attempt(key = key, loader = loader, isFirst = false)
        return value
    }

    fun lookup(key: K, isFirst: Boolean): Lookup<V> {
        val changes = changes()
        val lookup = store.lookup(key, isFirst, changes)
        reporter.changed(changes)
        if (isFirst) reporter.lookedUp(key, isHit = lookup is Lookup.Hit)
        return lookup
    }

    inline fun lead(key: K, flight: Flight<V>, load: () -> Result<V>): V {
        val started = timeSource.markNow()
        var landing: Settled<V> = Settled.Abandoned
        val done = try {
            doneOf(key, load()).also { settled -> landing = settled }
        } finally {
            land(key, flight, landing)
        }
        return concluded(key, done, started)
    }

    fun doneOf(key: K, result: Result<V>): Settled.Done<V> = result.fold(
        onSuccess = { value -> loadedOf(key, value) },
        onFailure = { failure -> Settled.Failed(failure) },
    )

    fun land(key: K, flight: Flight<V>, settled: Settled<V>) {
        val changes = changes()
        try {
            store.land(key = key, flight = flight, settled = settled, changes = changes)
        } finally {
            flight.settle(settled)
        }
        reporter.changed(changes)
    }

    fun concluded(key: K, done: Settled.Done<V>, started: ComparableTimeMark): V {
        val elapsed = started.elapsedNow()
        return when (done) {
            is Settled.Loaded -> {
                reporter.loaded(key, elapsed)
                done.value
            }

            is Settled.Failed -> {
                reporter.loadFailed(key = key, elapsed = elapsed, failure = done.cause)
                servedOr(key) { done.cause }
            }
        }
    }

    fun joined(key: K, settled: Settled<V>): V? = when (settled) {
        is Settled.Loaded -> settled.value
        is Settled.Failed -> servedOr(key) { CacheLoadException(name, settled.cause) }
        Settled.Abandoned -> null
    }

    private suspend fun attempt(key: K, loader: suspend (K) -> V, isFirst: Boolean): V? =
        when (val lookup = lookup(key, isFirst)) {
            is Lookup.Hit -> lookup.value
            is Lookup.Lead -> lead(key, lookup.flight) { attempted { loader(key) } }
            is Lookup.Join -> joined(key, lookup.flight.await())
        }

    private fun loadedOf(key: K, value: V): Settled.Done<V> = sizer.measured(key, value).fold(
        onSuccess = { sizing -> Settled.Loaded(value, sizing) },
        onFailure = { failure -> Settled.Failed(failure) },
    )

    private fun servedOr(key: K, failure: () -> Throwable): V {
        val served = fallbackOf(key) ?: throw failure()
        if (served.isStale) reporter.staleServed(key)
        return served.value
    }

    private fun fallbackOf(key: K): Fallback<V>? {
        if (!isServingStale) return null
        return store.fallback(key)
    }

    private fun changes(): Changes<K> = Changes(isRecordingKeys)
}
