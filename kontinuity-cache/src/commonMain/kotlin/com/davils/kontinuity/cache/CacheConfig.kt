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

import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.validateOrThrow
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

private const val SUBJECT: String = "cache configuration"

/**
 * How a [Cache] bounds, evicts and expires its entries.
 *
 * A configuration is an immutable value, validated when it is built and safe to share between caches and threads.
 * It holds the [weigher] and the variable lifetime [expireAfter] of the caller, which the cache runs as foreign code:
 * never while it holds its lock, and always contained.
 *
 * @param K the type of the keys.
 * @param V the type of the values.
 * @param builder the builder that holds the values.
 * @since 1.0.0
 */
public class CacheConfig<K : Any, V : Any> internal constructor(builder: CacheBuilder<K, V>) : Validatable {
    private val isBoundTwice: Boolean = builder.isMaximumSizeSet && builder.maximumWeight != null

    /**
     * The most entries the cache holds: 1,000 by default, from 1 to [HARD_MAXIMUM_SIZE]. A cache bounded by
     * [maximumWeight] holds at most [HARD_MAXIMUM_SIZE] entries, whatever they weigh.
     *
     * @since 1.0.0
     */
    public val maximumSize: Int = boundOf(builder)

    /**
     * The most the entries weigh together, or null for a cache bounded by [maximumSize]: from 1 to
     * [HARD_MAXIMUM_WEIGHT], and only together with a [weigher]. An entry heavier than the bound alone is never kept.
     *
     * @since 1.0.0
     */
    public val maximumWeight: Long? = builder.maximumWeight

    /**
     * The weight of an entry, from 0 to [Int.MAX_VALUE], or null for a cache bounded by [maximumSize], in which every
     * entry weighs 1. It runs outside the lock of the cache; a weigher that throws or returns a negative weight fails
     * the write with an [IllegalArgumentException].
     *
     * @since 1.0.0
     */
    public val weigher: ((key: K, value: V) -> Int)? = builder.weigher

    /**
     * Which entry a full cache gives up: [Eviction.Lru] by default.
     *
     * @since 1.0.0
     */
    public val eviction: Eviction = builder.eviction

    /**
     * How long an entry lives after it was written: infinite by default, otherwise positive and at most
     * [HARD_DURATION].
     *
     * @since 1.0.0
     */
    public val expireAfterWrite: Duration = builder.expireAfterWrite

    /**
     * How long an entry lives after it was last read or written: infinite by default, otherwise positive and at most
     * [HARD_DURATION]. Only a read that returns the entry, such as [Cache.get] or a hit of [Cache.getOrLoad], counts
     * as an access; [Cache.peek] and [Cache.contains] do not.
     *
     * @since 1.0.0
     */
    public val expireAfterAccess: Duration = builder.expireAfterAccess

    /**
     * The lifetime of an entry computed from its key and value at every write, or null for none. It shortens
     * [expireAfterWrite] and never extends it. Its result is clamped to zero and [HARD_DURATION], where an infinite
     * result stays infinite; a result of zero or less and a function that throws keep the entry from being stored.
     *
     * @since 1.0.0
     */
    public val expireAfter: ((key: K, value: V) -> Duration)? = builder.expireAfter

    /**
     * How long an expired entry is kept to be served when its reload by [Cache.getOrLoad] fails: zero by default,
     * which serves no stale values, and at most [HARD_DURATION]. A stale entry is never returned by [Cache.get],
     * [Cache.peek] or [Cache.contains].
     *
     * @since 1.0.0
     */
    public val staleOnFailure: Duration = builder.staleOnFailure

    /**
     * How often the reaper removes the expired entries on the scope of the environment, or null for no reaper: from
     * one millisecond to [HARD_DURATION]. A cache with a reaper needs an environment with a scope.
     *
     * @since 1.0.0
     */
    public val reapEvery: Duration? = builder.reapEvery

    /**
     * Whether the events name the keys: false by default, so that a key, which may hold personal data or come from
     * an attacker, never leaves the cache. When true, an event shows the escaped `toString` of the key.
     *
     * @since 1.0.0
     */
    public val isRecordingKeys: Boolean = builder.isRecordingKeys

    init {
        validateOrThrow(SUBJECT)
    }

    /**
     * Checks every value against its bounds.
     *
     * @receiver the validation that collects the violations.
     * @since 1.0.0
     */
    override fun ValidationScope.validate() {
        inRange(property = ::maximumSize, value = maximumSize, range = 1..HARD_MAXIMUM_SIZE)
        maximumWeight?.let { bound -> inRange(property = ::maximumWeight, value = bound, range = WEIGHTS) }
        if (isBoundTwice) fail(property = ::maximumWeight, message = "must not be set together with maximumSize")
        val isWeighed = maximumWeight != null
        if (isWeighed != (weigher != null)) fail(property = ::weigher, message = "must be set with maximumWeight")
        lifetime(::expireAfterWrite, expireAfterWrite)
        lifetime(::expireAfterAccess, expireAfterAccess)
        inRange(property = ::staleOnFailure, value = staleOnFailure, range = Duration.ZERO..HARD_DURATION)
        reapEvery?.let { every -> inRange(property = ::reapEvery, value = every, range = SHORTEST_REAP..HARD_DURATION) }
    }

    /**
     * Shows the bounds, the policy and the lifetimes, never the functions.
     *
     * @return the configuration as text.
     * @since 1.0.0
     */
    override fun toString(): String = "CacheConfig(maximumSize=$maximumSize, maximumWeight=${maximumWeight ?: NONE}, " +
        "eviction=$eviction, expireAfterWrite=$expireAfterWrite, expireAfterAccess=$expireAfterAccess, " +
        "variableExpiry=${expireAfter != null}, staleOnFailure=$staleOnFailure, reapEvery=${reapEvery ?: NONE}, " +
        "isRecordingKeys=$isRecordingKeys)"

    private fun ValidationScope.lifetime(property: KProperty<*>, value: Duration) {
        if (value == Duration.INFINITE) return
        val isAllowed = value.isPositive() && value <= HARD_DURATION
        if (!isAllowed) fail(property = property, message = "must be positive and at most 365 days, or infinite")
    }

    /**
     * The hard ceilings of every cache.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The most entries of one cache: 10,000,000.
         *
         * @since 1.0.0
         */
        public const val HARD_MAXIMUM_SIZE: Int = 10_000_000

        /**
         * The highest [maximumWeight]: 1,000,000,000,000.
         *
         * @since 1.0.0
         */
        public const val HARD_MAXIMUM_WEIGHT: Long = 1_000_000_000_000L

        /**
         * The longest finite lifetime, grace and reaper interval: 365 days.
         *
         * @since 1.0.0
         */
        public val HARD_DURATION: Duration = 365.days

        private const val NONE: String = "none"
        internal const val DEFAULT_MAXIMUM_SIZE: Int = 1_000
        private val SHORTEST_REAP: Duration = 1.milliseconds
        private val WEIGHTS: LongRange = 1L..HARD_MAXIMUM_WEIGHT

        private fun boundOf(builder: CacheBuilder<*, *>): Int {
            if (builder.maximumWeight == null) return builder.maximumSize
            return HARD_MAXIMUM_SIZE
        }
    }
}
