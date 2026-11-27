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

package com.davils.kontinuity.ratelimiter

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.environment
import com.davils.kontinuity.ratelimiter.internal.KeyedMeters
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.collection.OrderedMap
import com.davils.kore.core.concurrency.Guard
import com.davils.kore.core.concurrency.guard
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * A rate limiter per key, such as per principal or per address of a client, so that one caller cannot spend the
 * permits of every other.
 *
 * Every key gets a [RateLimiter] of its own with the same [config], created when the key first calls. A key that has
 * not called for [idleTimeout] is forgotten once the limiter is full, and comes back with a full limit. The limiter
 * holds at most [maxKeys] keys: a new key that finds it full while no key is idle is refused with a
 * [RequestNotPermittedException], so a flood of new keys fails closed instead of pushing the keys that are in use
 * out. Keys are kept in an [OrderedMap] of Kore and found by comparison, never by hash code, so keys an attacker
 * chose cannot make a lookup slow.
 *
 * Its telemetry runs through the environment it was created with: the counter `kontinuity.keyed_rate_limiter.calls`,
 * labelled by `name` and `outcome` (`permitted`, `rejected` or `overflowed`), and the gauge
 * `kontinuity.keyed_rate_limiter.keys`, labelled by `name`. The limiters of the keys report nothing of their own, so
 * the number of time series never grows with the number of keys. The limiter is safe to share between coroutines.
 *
 * @param K the type of the keys; its order must be total and consistent.
 * @property name the name of the limiter.
 * @property config the configuration of the limiter of every key.
 * @property maxKeys the most keys the limiter holds at once.
 * @property idleTimeout how long a key may stay unused before it may be forgotten.
 * @param environment the time source, entropy and telemetry of the limiter.
 * @since 1.1.0
 */
public class KeyedRateLimiter<K : Comparable<K>> internal constructor(
    public val name: InstanceName,
    public val config: RateLimiterConfig,
    public val maxKeys: Int,
    public val idleTimeout: Duration,
    environment: Environment,
) {
    private val timeSource = environment.timeSource
    private val quiet: Environment = quietOf(environment)
    private val meters: KeyedMeters? = environment.meters?.let { registry -> KeyedMeters(registry, name) }
    private val guard: Guard = guard()
    private var slots: OrderedMap<K, Slot> = OrderedMap()
    private var nextExpiry: ComparableTimeMark? = null

    init {
        require(maxKeys in 1..HARD_MAX_KEYS) { "The most keys must be between 1 and $HARD_MAX_KEYS" }
        require(idleTimeout >= config.limitRefreshPeriod && idleTimeout <= HARD_IDLE_TIMEOUT) {
            "The idle timeout must be at least the refresh period of the limit and at most $HARD_IDLE_TIMEOUT"
        }
    }

    /**
     * How many keys the limiter holds now.
     *
     * @since 1.1.0
     */
    public val keys: Int get() = guard.locked { slots.size }

    /**
     * Takes [permits] permits of the limiter of [key], waiting for them up to the timeout of [config], and runs
     * [block].
     *
     * @param T the type of the result.
     * @param key the key whose permits are spent.
     * @param permits how many permits the call needs, from 1 to the limit for period of [config]; one by default.
     * @param block the protected call.
     * @return the result of [block].
     * @throws IllegalArgumentException if [permits] is out of its bounds.
     * @throws RequestNotPermittedException if the permits of [key] are not free within the timeout, or if [key] is
     *   new and the limiter is full of keys that are in use.
     * @throws Throwable what [block] throws.
     * @since 1.1.0
     */
    public suspend fun <T> execute(key: K, permits: Int = 1, block: suspend () -> T): T {
        val limiter = limiterOf(key, permits)
        try {
            limiter.acquire(permits)
        } catch (refused: RequestNotPermittedException) {
            meters?.counted(isPermitted = false)
            throw refused
        }
        meters?.counted(isPermitted = true)
        return block()
    }

    /**
     * Takes [permits] permits of the limiter of [key] if they are free now; never waits.
     *
     * @param key the key whose permits are spent.
     * @param permits how many permits to take, from 1 to the limit for period of [config]; one by default.
     * @return true if the permits were taken, false if the limiter of [key] refused them or the limiter is full of
     *   keys that are in use.
     * @throws IllegalArgumentException if [permits] is out of its bounds.
     * @since 1.1.0
     */
    public fun tryAcquire(key: K, permits: Int = 1): Boolean {
        val limiter = guard.locked { slotOf(key) }?.limiter ?: return false
        val isTaken = limiter.tryAcquire(permits)
        meters?.counted(isPermitted = isTaken)
        return isTaken
    }

    /**
     * Shows the name, the number of keys and their bound.
     *
     * @return the limiter as text.
     * @since 1.1.0
     */
    override fun toString(): String = "KeyedRateLimiter($name, keys=$keys/$maxKeys)"

    private fun limiterOf(key: K, permits: Int): RateLimiter {
        val slot = guard.locked { slotOf(key) }
        return slot?.limiter ?: throw RequestNotPermittedException(name, permits, availableIn())
    }

    private fun slotOf(key: K): Slot? {
        val now = timeSource.markNow()
        val known = slots[key] ?: return created(key, now)
        return known.touched(now)
    }

    private fun created(key: K, now: ComparableTimeMark): Slot? {
        if (slots.size >= maxKeys) forgetIdle(now)
        if (slots.size >= maxKeys) return overflowed()
        val created = Slot(RateLimiter(name, config, quiet), now)
        slots[key] = created
        meters?.keys(slots.size)
        return created
    }

    private fun overflowed(): Slot? {
        meters?.overflowed()
        return null
    }

    private fun forgetIdle(now: ComparableTimeMark) {
        val expiry = nextExpiry
        if (expiry != null && now < expiry) return
        val kept = OrderedMap<K, Slot>()
        var earliest: ComparableTimeMark? = null
        slots.entries.forEach { (key, slot) ->
            val expiresAt = slot.usedAt + idleTimeout
            if (expiresAt > now) {
                kept[key] = slot
                earliest = minOf(a = earliest ?: expiresAt, b = expiresAt)
            }
        }
        slots = kept
        nextExpiry = earliest
        meters?.keys(slots.size)
    }

    private fun availableIn(): Duration {
        val expiry = guard.locked { nextExpiry } ?: return idleTimeout
        return (-expiry.elapsedNow()).coerceAtLeast(Duration.ZERO)
    }

    private class Slot(val limiter: RateLimiter, var usedAt: ComparableTimeMark) {
        fun touched(now: ComparableTimeMark): Slot {
            usedAt = now
            return this
        }
    }

    /**
     * The bounds of a keyed rate limiter.
     *
     * @since 1.1.0
     */
    public companion object {
        /**
         * The most keys a limiter holds unless another bound is given: 10,000.
         *
         * @since 1.1.0
         */
        public const val DEFAULT_MAX_KEYS: Int = 10_000

        /**
         * The hard ceiling of the keys of a limiter: 1,000,000.
         *
         * @since 1.1.0
         */
        public const val HARD_MAX_KEYS: Int = 1_000_000

        /**
         * How long a key may stay unused unless another time is given: 10 minutes.
         *
         * @since 1.1.0
         */
        public val DEFAULT_IDLE_TIMEOUT: Duration = 10.minutes

        /**
         * The longest idle timeout of a limiter: one day.
         *
         * @since 1.1.0
         */
        public val HARD_IDLE_TIMEOUT: Duration = 1.days

        private fun quietOf(environment: Environment): Environment = environment {
            timeSource = environment.timeSource
            entropy = environment.entropy
            environment.scope?.let { scope -> scope(scope) }
        }
    }
}
