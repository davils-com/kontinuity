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

package com.davils.kontinuity.cache.fixture

import com.davils.kontinuity.cache.Eviction

internal class ReferenceModel(private val capacity: Int, private val eviction: Eviction) {
    private val ranks: MutableMap<Int, Rank> = mutableMapOf()
    private var tick: Long = 0L

    val keys: Set<Int> get() = ranks.keys.toSet()

    fun get(key: Int): Boolean {
        val rank = ranks[key] ?: return false
        tick += 1L
        ranks[key] = when (eviction) {
            Eviction.Lru -> Rank(uses = 0L, since = tick)
            Eviction.Lfu -> Rank(uses = rank.uses + 1L, since = tick)
            Eviction.Fifo -> rank
        }
        return true
    }

    fun put(key: Int) {
        tick += 1L
        val uses = ranks[key]?.uses ?: 0L
        ranks[key] = when (eviction) {
            Eviction.Lru, Eviction.Fifo -> Rank(uses = 0L, since = tick)
            Eviction.Lfu -> Rank(uses = uses + 1L, since = tick)
        }
        if (ranks.size > capacity) evictOtherThan(key)
    }

    private fun evictOtherThan(key: Int) {
        val candidates = ranks.filterKeys { candidate -> candidate != key }
        val order = compareBy<Map.Entry<Int, Rank>>({ entry -> entry.value.uses }, { entry -> entry.value.since })
        val victim = candidates.entries.minWith(order)
        ranks.remove(victim.key)
    }

    private data class Rank(val uses: Long, val since: Long)
}
