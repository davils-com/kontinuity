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

package com.davils.kontinuity.cache

import com.davils.kontinuity.cache.internal.CacheMeters
import com.davils.kontinuity.cache.internal.Changes
import com.davils.kontinuity.cache.internal.Loads
import com.davils.kontinuity.cache.internal.Reporter
import com.davils.kontinuity.cache.internal.Sizer
import com.davils.kontinuity.cache.internal.Store
import com.davils.kontinuity.cache.internal.reap
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.Instance
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kore.core.annotation.DavilsInternal
import kotlinx.coroutines.flow.Flow

/**
 * A bounded in-memory cache with single-flight loading, the counterpart of the cache of resilience4j without a
 * store behind it.
 *
 * Create one with `cache<String, User>("users") { maximumSize = 10_000; expireAfterWrite = 5.minutes }`. The cache
 * holds at most [CacheConfig.maximumSize] entries or, with a weigher, at most [CacheConfig.maximumWeight]; a write
 * that exceeds a bound evicts entries by the [Eviction] policy in constant time per entry. Entries expire after
 * [CacheConfig.expireAfterWrite], [CacheConfig.expireAfterAccess] or the lifetime of [CacheConfig.expireAfter],
 * whichever ends first. An expired entry is removed when a lookup or an eviction meets it, by [cleanUp], or by the
 * reaper of [CacheConfig.reapEvery]; until then it is invisible to every read but counts in [size].
 *
 * [getOrLoad] loads a missing value once for every caller: concurrent misses of one key share one load. A
 * [put], [putIfAbsent], [remove] or [clear] during a load invalidates it, so that a load that started before a write
 * never overwrites the newer value or brings back a removed key; the callers of the invalidated load still receive
 * its value. With [CacheConfig.staleOnFailure], a failed load serves the expired value of the key instead.
 *
 * A cache is safe to use from many coroutines and threads at once. Every operation holds the lock of the cache only
 * for constant work, except [clear], which empties its table in one step, and [cleanUp], which releases its lock after
 * every few hundred entries; the lock is never held while a loader, a weigher, the lifetime function or the
 * `toString` of a key runs, and nothing suspends or blocks while holding it. Keys are hashed by the `hashCode` and
 * `equals` of their type, so keys from an attacker need a type whose hashing an attacker cannot steer.
 *
 * @param K the type of the keys; a key must not change its `hashCode` or `equals` while it is in the cache.
 * @param V the type of the values.
 * @property name the name of the cache.
 * @property config the configuration.
 * @param environment the environment of the cache.
 * @since 1.0.0
 */
public class Cache<K : Any, V : Any> internal constructor(
    override val name: InstanceName,
    public val config: CacheConfig<K, V>,
    environment: Environment,
) : Instance {
    private val sink = EventSink(environment, CacheEvent.TOPIC)
    private val store: Store<K, V> = Store(config, environment.timeSource)
    private val sizer: Sizer<K, V> = Sizer(name, config)
    private val reporter: Reporter<K> = Reporter(
        name = name,
        sink = sink,
        meters = environment.meters?.let { registry -> CacheMeters(registry, name) },
        isRecordingKeys = config.isRecordingKeys,
    )

    internal val loads: Loads<K, V> = Loads(
        name = name,
        store = store,
        sizer = sizer,
        reporter = reporter,
        timeSource = environment.timeSource,
        isRecordingKeys = config.isRecordingKeys,
        isServingStale = config.staleOnFailure.isPositive(),
    )

    init {
        val every = config.reapEvery
        val scope = environment.scope
        require(every == null || scope != null) { "The cache $name needs the scope of its environment for its reaper" }
        if (every != null) scope?.reap(every) { sweep(store, reporter, config.isRecordingKeys) }
    }

    override val kind: ResilienceKind get() = ResilienceKind.Cache

    /**
     * The events of the cache, hot and without replay; a slow collector loses the oldest events.
     *
     * @since 1.0.0
     */
    public val events: Flow<CacheEvent> get() = sink.events

    /**
     * The number of entries, in constant time. It includes the expired entries that no lookup, eviction, [cleanUp]
     * or reaper has removed yet and the stale entries kept for [CacheConfig.staleOnFailure]; call [cleanUp] first
     * for the number of live entries. Reading it changes nothing.
     *
     * @since 1.0.0
     */
    public val size: Int get() = store.size

    /**
     * Returns the live value of [key] and records the access: a hit moves the entry in the [Eviction] order and
     * renews its [CacheConfig.expireAfterAccess]. The lookup counts as a hit or a miss; an expired entry it meets is
     * removed, unless it is kept as a stale value for [CacheConfig.staleOnFailure].
     *
     * @param key the key.
     * @return the value, or null if the cache holds no live value for [key].
     * @since 1.0.0
     */
    public operator fun get(key: K): V? {
        val changes = changes()
        val value = store.get(key, changes)
        reporter.changed(changes)
        reporter.lookedUp(key, isHit = value != null)
        return value
    }

    /**
     * Returns the live value of [key] without recording an access or counting a lookup; reading it changes nothing.
     *
     * @param key the key.
     * @return the value, or null if the cache holds no live value for [key].
     * @since 1.0.0
     */
    public fun peek(key: K): V? = store.peek(key)

    /**
     * Tells whether the cache holds a live value for [key], without recording an access; reading it changes nothing.
     *
     * @param key the key.
     * @return true if [peek] would return a value.
     * @since 1.0.0
     */
    public operator fun contains(key: K): Boolean = store.peek(key) != null

    /**
     * Returns the live value of [key] or loads, stores and returns it, loading it at most once at a time.
     *
     * A hit returns at once. On a miss, the first caller runs [loader] in its own coroutine and every concurrent
     * caller for the same key waits for that load instead of running its own: the waiters receive its value or a
     * [CacheLoadException] of their own that wraps its failure, while the loading caller receives the failure of the
     * loader itself. When the loading caller is cancelled, its load ends without a value and one of the waiters loads
     * again. A waiter that is cancelled stops waiting without affecting the load. The wait for a load is as long as
     * the load: bound it by bounding [loader], such as with a time limiter.
     *
     * A loaded value is stored only if no [put], [putIfAbsent], [remove] or [clear] touched the key while it loaded.
     * When the load fails and [CacheConfig.staleOnFailure] is positive, an expired value of the key within its grace
     * is returned instead and reported as served stale. The call counts once as a hit or a miss; a load counts as a
     * load or a load failure. A cancellation of [loader] that does not come from the caller is a failure, as
     * `attempted` of Kore reports it.
     *
     * @param key the key.
     * @param loader loads the value of a missing key; it runs outside the lock of the cache.
     * @return the value.
     * @throws CacheLoadException if this call waited for the load of another caller and that load failed.
     * @throws IllegalArgumentException if the weigher fails for the loaded value or returns a negative weight.
     * @throws Throwable what [loader] throws, if this call ran the load.
     * @since 1.0.0
     */
    public suspend fun getOrLoad(key: K, loader: suspend (K) -> V): V = loads.load(key, loader)

    /**
     * Stores [value] for [key], replacing the value present, and invalidates a load of [key] in flight. A write that
     * exceeds a bound evicts other entries; a value heavier than [CacheConfig.maximumWeight] alone or with a lifetime
     * of zero is not stored and removes the present value.
     *
     * @param key the key.
     * @param value the value.
     * @throws IllegalArgumentException if the weigher fails or returns a negative weight; the cache is then unchanged.
     * @since 1.0.0
     */
    public fun put(key: K, value: V) {
        val sizing = sizer.measured(key, value).getOrThrow()
        val changes = changes()
        store.put(key = key, value = value, sizing = sizing, changes = changes)
        reporter.changed(changes)
    }

    /**
     * Stores [value] for [key] unless the cache holds a live value for it; a stored value invalidates a load of [key]
     * in flight. The weigher and the lifetime function run even when a value is present.
     *
     * @param key the key.
     * @param value the value.
     * @return the live value present, which stays, or null if [value] was written.
     * @throws IllegalArgumentException if the weigher fails or returns a negative weight; the cache is then unchanged.
     * @since 1.0.0
     */
    public fun putIfAbsent(key: K, value: V): V? {
        val sizing = sizer.measured(key, value).getOrThrow()
        val changes = changes()
        val present = store.putIfAbsent(key = key, value = value, sizing = sizing, changes = changes)
        reporter.changed(changes)
        return present
    }

    /**
     * Removes the entry of [key], expired or not, and invalidates a load of [key] in flight.
     *
     * @param key the key.
     * @return the live value removed, or null if there was none.
     * @since 1.0.0
     */
    public fun remove(key: K): V? {
        val changes = changes()
        val removed = store.remove(key, changes)
        reporter.changed(changes)
        return removed
    }

    /**
     * Removes every entry and invalidates every load in flight; the counts of [snapshot] keep counting.
     *
     * @since 1.0.0
     */
    public fun clear() {
        val changes = changes()
        store.clear(changes)
        reporter.changed(changes)
    }

    /**
     * Removes every expired entry, except the stale ones kept for [CacheConfig.staleOnFailure], and returns how many
     * it removed.
     *
     * It walks the entries in chunks of a few hundred and releases the lock of the cache between them, so that other
     * callers are never held up by a long sweep. Entries written or moved while it runs may be visited twice or not
     * at all; it visits at most twice the entries the cache held when it started.
     *
     * @return the number of expired entries removed.
     * @since 1.0.0
     */
    public fun cleanUp(): Int = sweep(store, reporter, config.isRecordingKeys)

    /**
     * Returns the size, the weight and the counts of the cache at this moment.
     *
     * @return the snapshot.
     * @since 1.0.0
     */
    public fun snapshot(): CacheSnapshot = store.snapshot()

    /**
     * Returns a function that calls [getOrLoad] with [loader] for every key it is called with.
     *
     * @param loader loads the value of a missing key.
     * @return the caching function.
     * @since 1.0.0
     */
    public fun decorate(loader: suspend (K) -> V): suspend (K) -> V = { key -> getOrLoad(key, loader) }

    /**
     * Shows the name and the number of entries.
     *
     * @return the cache as text.
     * @since 1.0.0
     */
    override fun toString(): String = "Cache($name, size=$size)"

    private fun changes(): Changes<K> = Changes(config.isRecordingKeys)

    private companion object {
        fun <K : Any, V : Any> sweep(store: Store<K, V>, reporter: Reporter<K>, isRecordingKeys: Boolean): Int {
            val sweep = store.startSweep()
            var removed = 0
            try {
                do {
                    val changes = Changes<K>(isRecordingKeys)
                    val isUnfinished = store.sweep(sweep, changes)
                    removed += changes.expired.count
                    reporter.changed(changes)
                } while (isUnfinished)
            } finally {
                store.endSweep(sweep)
            }
            return removed
        }
    }
}
