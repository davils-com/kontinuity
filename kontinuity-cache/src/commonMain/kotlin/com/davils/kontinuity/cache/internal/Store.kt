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

package com.davils.kontinuity.cache.internal

import com.davils.kontinuity.cache.CacheConfig
import com.davils.kontinuity.cache.CacheSnapshot
import com.davils.kontinuity.core.telemetry.Guard
import com.davils.kontinuity.core.telemetry.guard
import com.davils.kore.core.annotation.DavilsInternal
import kotlin.time.TimeSource

internal class Store<K : Any, V : Any>(
    config: CacheConfig<K, V>,
    private val timeSource: TimeSource.WithComparableMarks,
) {
    private val guard: Guard = guard()
    private val counts: Counts = Counts()
    private val entries: Entries<K, V> = Entries(config, counts)
    private val flights: HashMap<K, Flight<V>> = HashMap()

    val size: Int get() = guard.locked { entries.size }

    fun get(key: K, changes: Changes<K>): V? = guard.locked {
        val value = entries.read(key, timeSource.markNow(), changes)?.value
        counts.lookedUp(isHit = value != null)
        value
    }

    fun peek(key: K): V? = guard.locked { entries.live(key, timeSource.markNow())?.value }

    fun lookup(key: K, isFirst: Boolean, changes: Changes<K>): Lookup<V> = guard.locked {
        val value = entries.read(key, timeSource.markNow(), changes)?.value
        if (isFirst) counts.lookedUp(isHit = value != null)
        value?.let { found -> Lookup.Hit(found) } ?: flightOf(key)
    }

    fun land(key: K, flight: Flight<V>, settled: Settled<V>, changes: Changes<K>) {
        guard.locked {
            val isCurrent = flights[key] === flight
            if (isCurrent) flights.remove(key)
            counts.settled(settled)
            val loaded = (settled as? Settled.Loaded<V>)?.takeIf { isCurrent }
            loaded?.let { value -> stored(key, value, changes) }
        }
    }

    fun fallback(key: K): Fallback<V>? = guard.locked {
        val now = timeSource.markNow()
        val node = entries.fallback(key, now)
        val isStale = node != null && entries.isStale(node, now)
        if (isStale) counts.staleServed += 1L
        node?.let { found -> Fallback(value = found.value, isStale = isStale) }
    }

    fun put(key: K, value: V, sizing: Sizing, changes: Changes<K>) {
        guard.locked {
            flights.remove(key)
            entries.write(key = key, value = value, sizing = sizing, now = timeSource.markNow(), changes = changes)
        }
    }

    fun putIfAbsent(key: K, value: V, sizing: Sizing, changes: Changes<K>): V? = guard.locked {
        val now = timeSource.markNow()
        val present = entries.live(key, now)?.value
        if (present == null) {
            flights.remove(key)
            entries.write(key = key, value = value, sizing = sizing, now = now, changes = changes)
        }
        present
    }

    fun remove(key: K, changes: Changes<K>): V? = guard.locked {
        flights.remove(key)
        entries.remove(key, timeSource.markNow(), changes)?.value
    }

    fun clear(changes: Changes<K>) {
        guard.locked {
            flights.clear()
            entries.clear(changes)
        }
    }

    fun startSweep(): Sweep<K, V> = guard.locked { entries.startSweep() }

    fun sweep(sweep: Sweep<K, V>, changes: Changes<K>): Boolean =
        guard.locked { entries.sweep(sweep, timeSource.markNow(), changes) }

    fun endSweep(sweep: Sweep<K, V>) {
        guard.locked { entries.endSweep(sweep) }
    }

    fun snapshot(): CacheSnapshot = guard.locked {
        CacheSnapshot(
            size = entries.size,
            weight = entries.weight,
            hits = counts.hits,
            misses = counts.misses,
            loads = counts.loads,
            loadFailures = counts.loadFailures,
            evictions = counts.evictions,
            expirations = counts.expirations,
            staleServed = counts.staleServed,
        )
    }

    private fun flightOf(key: K): Lookup<V> {
        val running = flights[key]
        if (running != null) return Lookup.Join(running)
        val flight = Flight<V>()
        flights[key] = flight
        return Lookup.Lead(flight)
    }

    private fun stored(key: K, loaded: Settled.Loaded<V>, changes: Changes<K>) {
        entries.write(
            key = key,
            value = loaded.value,
            sizing = loaded.sizing,
            now = timeSource.markNow(),
            changes = changes,
        )
    }
}
