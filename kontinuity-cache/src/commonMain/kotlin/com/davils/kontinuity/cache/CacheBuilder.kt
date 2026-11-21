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

import com.davils.kore.core.annotation.DavilsDsl
import com.davils.kore.core.dsl.ValidatingBuilder
import kotlin.time.Duration

/**
 * Builds a [CacheConfig]: `cache<String, User>("users") { maximumSize = 10_000; expireAfterWrite = 5.minutes }`.
 *
 * Every property starts from its default; the configuration is validated as a whole when the builder is evaluated,
 * and the report names every value out of its bounds. A builder is evaluated once and refuses changes afterwards.
 *
 * @param K the type of the keys.
 * @param V the type of the values.
 * @since 1.0.0
 */
@DavilsDsl
public class CacheBuilder<K : Any, V : Any> internal constructor() : ValidatingBuilder<CacheConfig<K, V>>() {
    override val subject: String get() = "cache configuration"

    internal var isMaximumSizeSet: Boolean = false
        private set

    internal var weigher: ((key: K, value: V) -> Int)? = null
        private set

    internal var expireAfter: ((key: K, value: V) -> Duration)? = null
        private set

    /**
     * See [CacheConfig.maximumSize]; it cannot be set together with [maximumWeight].
     *
     * @since 1.0.0
     */
    public var maximumSize: Int = CacheConfig.DEFAULT_MAXIMUM_SIZE
        set(value) {
            checkOpen()
            isMaximumSizeSet = true
            field = value
        }

    /**
     * See [CacheConfig.maximumWeight]; it needs a [weigher] and cannot be set together with [maximumSize].
     *
     * @since 1.0.0
     */
    public var maximumWeight: Long? = null
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CacheConfig.eviction].
     *
     * @since 1.0.0
     */
    public var eviction: Eviction = Eviction.Lru
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CacheConfig.expireAfterWrite].
     *
     * @since 1.0.0
     */
    public var expireAfterWrite: Duration = Duration.INFINITE
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CacheConfig.expireAfterAccess].
     *
     * @since 1.0.0
     */
    public var expireAfterAccess: Duration = Duration.INFINITE
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CacheConfig.staleOnFailure].
     *
     * @since 1.0.0
     */
    public var staleOnFailure: Duration = Duration.ZERO
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CacheConfig.reapEvery].
     *
     * @since 1.0.0
     */
    public var reapEvery: Duration? = null
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [CacheConfig.isRecordingKeys].
     *
     * @since 1.0.0
     */
    public var isRecordingKeys: Boolean = false
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * Sets [CacheConfig.weigher], which a cache bounded by [maximumWeight] needs: `weigher { _, value -> value.size }`.
     *
     * @param weigher returns the weight of an entry, from 0 to [Int.MAX_VALUE].
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun weigher(weigher: (key: K, value: V) -> Int) {
        checkOpen()
        this.weigher = weigher
    }

    /**
     * Sets [CacheConfig.expireAfter], the lifetime of an entry computed at every write:
     * `expireAfter { _, token -> token.validFor }`.
     *
     * @param lifetime returns how long the written entry lives.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun expireAfter(lifetime: (key: K, value: V) -> Duration) {
        checkOpen()
        expireAfter = lifetime
    }

    override fun assemble(): CacheConfig<K, V> = CacheConfig(this)
}
