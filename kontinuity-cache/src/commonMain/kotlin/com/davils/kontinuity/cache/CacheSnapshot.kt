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

/**
 * What a [Cache] holds and counted since it was created; taking a snapshot changes nothing.
 *
 * Every lookup of [Cache.get] and every call of [Cache.getOrLoad] counts exactly once as a hit or a miss, so
 * `hits + misses` is the number of lookups. Every load that ran a loader to its end counts once as a load or a load
 * failure; a load whose caller was cancelled counts as neither, and its waiters load again.
 *
 * @property size the entries held, the expired ones that were not yet removed included.
 * @property weight the weight of the entries held; every entry weighs 1 without a weigher.
 * @property hits the lookups that found a live value.
 * @property misses the lookups that found no live value.
 * @property loads the loads that returned a value.
 * @property loadFailures the loads that failed, by the loader or by the weigher.
 * @property evictions the entries evicted for size or weight.
 * @property expirations the entries removed because they expired.
 * @property staleServed the stale values served after a failed load.
 * @since 1.0.0
 */
public class CacheSnapshot internal constructor(
    public val size: Int,
    public val weight: Long,
    public val hits: Long,
    public val misses: Long,
    public val loads: Long,
    public val loadFailures: Long,
    public val evictions: Long,
    public val expirations: Long,
    public val staleServed: Long,
) {
    /**
     * The share of lookups that were hits, from 0 to 1, and 0 before the first lookup.
     *
     * @since 1.0.0
     */
    public val hitRatio: Double
        get() {
            val lookups = hits + misses
            if (lookups == 0L) return 0.0
            return hits.toDouble() / lookups
        }

    /**
     * Compares every count.
     *
     * @param other the value to compare with.
     * @return true if [other] is a snapshot with the same counts.
     * @since 1.0.0
     */
    override fun equals(other: Any?): Boolean = other is CacheSnapshot && counts() == other.counts()

    /**
     * Hashes every count.
     *
     * @return the hash code.
     * @since 1.0.0
     */
    override fun hashCode(): Int = counts().hashCode()

    /**
     * Shows every count and the hit ratio.
     *
     * @return the snapshot as text.
     * @since 1.0.0
     */
    override fun toString(): String = "CacheSnapshot(size=$size, weight=$weight, hits=$hits, misses=$misses, " +
        "loads=$loads, loadFailures=$loadFailures, evictions=$evictions, expirations=$expirations, " +
        "staleServed=$staleServed, hitRatio=$hitRatio)"

    private fun counts(): List<Long> =
        listOf(size.toLong(), weight, hits, misses, loads, loadFailures, evictions, expirations, staleServed)
}
