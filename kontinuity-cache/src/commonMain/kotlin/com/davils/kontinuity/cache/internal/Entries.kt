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

import com.davils.kontinuity.cache.CacheConfig
import com.davils.kontinuity.cache.Eviction
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration

internal class Entries<K : Any, V : Any>(config: CacheConfig<K, V>, private val counts: Counts) {
    private val maximumSize: Int = config.maximumSize
    private val maximumWeight: Long = config.maximumWeight ?: Long.MAX_VALUE
    private val accessLifetime: Duration = config.expireAfterAccess
    private val grace: Duration = config.staleOnFailure
    private val order: EvictionOrder<K, V> = orderOf(config.eviction)
    private val sweeps: MutableList<Sweep<K, V>> = mutableListOf()
    private val nodes: HashMap<K, Node<K, V>> = HashMap()

    val size: Int get() = nodes.size

    var weight: Long = 0L
        private set

    fun live(key: K, now: ComparableTimeMark): Node<K, V>? {
        val node = nodes[key] ?: return null
        return node.takeIf { freshnessOf(node, now) == Freshness.Live }
    }

    fun read(key: K, now: ComparableTimeMark, changes: Changes<K>): Node<K, V>? {
        val node = nodes[key] ?: return null
        val freshness = freshnessOf(node, now)
        if (freshness == Freshness.Dead) expire(node, changes)
        if (freshness == Freshness.Live) touch(node, now)
        return node.takeIf { freshness == Freshness.Live }
    }

    fun fallback(key: K, now: ComparableTimeMark): Node<K, V>? {
        val node = nodes[key] ?: return null
        return node.takeIf { freshnessOf(node, now) != Freshness.Dead }
    }

    fun isStale(node: Node<K, V>, now: ComparableTimeMark): Boolean = freshnessOf(node, now) == Freshness.Stale

    fun write(key: K, value: V, sizing: Sizing, now: ComparableTimeMark, changes: Changes<K>) {
        val isExpired = !sizing.lifetime.isPositive()
        val isTooHeavy = sizing.weight > maximumWeight
        if (isExpired || isTooHeavy) {
            refuse(key, isExpired, changes)
            return
        }
        val node = nodes[key]?.also { existing -> replace(existing, value, sizing.weight) }
            ?: admitted(key, value, sizing.weight)
        node.writeDeadline = deadlineAfter(now, sizing.lifetime)
        node.accessDeadline = deadlineAfter(now, accessLifetime)
        makeRoom(node, changes)
        changes.resized(nodes.size, weight)
    }

    fun remove(key: K, now: ComparableTimeMark, changes: Changes<K>): Node<K, V>? {
        val node = nodes[key] ?: return null
        val freshness = freshnessOf(node, now)
        discard(node)
        changes.resized(nodes.size, weight)
        return node.takeIf { freshness == Freshness.Live }
    }

    fun clear(changes: Changes<K>) {
        changes.cleared = nodes.size
        nodes.clear()
        order.clear()
        weight = 0L
        sweeps.forEach(Sweep<K, V>::stop)
        changes.resized(size = 0, weight = 0L)
    }

    fun startSweep(): Sweep<K, V> {
        val sweep = Sweep(next = order.first(), budget = 2L * nodes.size + SWEEP_CHUNK)
        sweeps += sweep
        return sweep
    }

    fun sweep(sweep: Sweep<K, V>, now: ComparableTimeMark, changes: Changes<K>): Boolean {
        var steps = 0
        var node = sweep.next
        while (node != null && steps < SWEEP_CHUNK && sweep.budget > 0L) {
            sweep.next = order.after(node)
            steps += 1
            sweep.budget -= 1L
            if (freshnessOf(node, now) == Freshness.Dead) expire(node, changes)
            node = sweep.next
        }
        return node != null && sweep.budget > 0L
    }

    fun endSweep(sweep: Sweep<K, V>) {
        sweeps.remove(sweep)
    }

    private fun refuse(key: K, isExpired: Boolean, changes: Changes<K>) {
        nodes[key]?.let(::discard)
        if (isExpired) changes.expired.add(key)
        if (isExpired) counts.expirations += 1L
        if (!isExpired) changes.evictedByWeight.add(key)
        if (!isExpired) counts.evictions += 1L
        changes.resized(nodes.size, weight)
    }

    private fun touch(node: Node<K, V>, now: ComparableTimeMark) {
        moveSweepsPast(node)
        order.accessed(node)
        node.accessDeadline = deadlineAfter(now, accessLifetime)
    }

    private fun replace(node: Node<K, V>, value: V, nodeWeight: Int) {
        weight += nodeWeight.toLong() - node.weight
        node.value = value
        node.weight = nodeWeight
        moveSweepsPast(node)
        order.rewritten(node)
    }

    private fun admitted(key: K, value: V, nodeWeight: Int): Node<K, V> {
        val node = Node(key = key, value = value, weight = nodeWeight)
        nodes[key] = node
        order.add(node)
        weight += nodeWeight.toLong()
        return node
    }

    private fun makeRoom(written: Node<K, V>, changes: Changes<K>) {
        var victim = victimFor(written)
        while (victim != null) {
            val isOverSize = nodes.size > maximumSize
            discard(victim)
            counts.evictions += 1L
            if (isOverSize) changes.evictedBySize.add(victim.key)
            if (!isOverSize) changes.evictedByWeight.add(victim.key)
            victim = victimFor(written)
        }
    }

    private fun victimFor(written: Node<K, V>): Node<K, V>? {
        val isOver = nodes.size > maximumSize || weight > maximumWeight
        val first = order.first()?.takeIf { isOver } ?: return null
        return first.takeIf { node -> node !== written } ?: order.after(first)
    }

    private fun expire(node: Node<K, V>, changes: Changes<K>) {
        discard(node)
        counts.expirations += 1L
        changes.expired.add(node.key)
        changes.resized(nodes.size, weight)
    }

    private fun discard(node: Node<K, V>) {
        moveSweepsPast(node)
        order.remove(node)
        nodes.remove(node.key)
        weight -= node.weight.toLong()
    }

    private fun moveSweepsPast(node: Node<K, V>) {
        if (sweeps.isEmpty()) return
        val following = order.after(node)
        sweeps.forEach { sweep -> sweep.movePast(node, following) }
    }

    private fun freshnessOf(node: Node<K, V>, now: ComparableTimeMark): Freshness {
        val deadline = node.deadline()
        if (deadline == null || now < deadline) return Freshness.Live
        return Freshness.Stale.takeIf { now < deadline + grace } ?: Freshness.Dead
    }

    private companion object {
        const val SWEEP_CHUNK: Int = 256

        fun <K : Any, V : Any> orderOf(eviction: Eviction): EvictionOrder<K, V> = when (eviction) {
            Eviction.Lru -> RecencyOrder(isAccessOrdered = true)
            Eviction.Lfu -> FrequencyOrder()
            Eviction.Fifo -> RecencyOrder(isAccessOrdered = false)
        }

        fun deadlineAfter(now: ComparableTimeMark, lifetime: Duration): ComparableTimeMark? {
            if (lifetime == Duration.INFINITE) return null
            return now + lifetime
        }
    }
}
