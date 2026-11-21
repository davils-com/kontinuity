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

import com.davils.konfetti.core.TopicKey
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceEvent
import com.davils.kontinuity.core.ResilienceKind
import kotlin.time.Duration

/**
 * An event of a [Cache]: a lookup, a load, an eviction, an expiry, a stale value served or a clear.
 *
 * An event names a key only when the cache records keys ([CacheConfig.isRecordingKeys]); the key is then the
 * `toString` of the key escaped by `SafeText` of Kore and shortened to its display length, and a key whose `toString`
 * throws is shown as `<unshowable>`. Otherwise a key is null and a list of keys is empty, and only the counts tell how
 * many entries an event concerns.
 *
 * @since 1.0.0
 */
public sealed interface CacheEvent : ResilienceEvent {
    override val kind: ResilienceKind get() = ResilienceKind.Cache

    /**
     * A lookup found a live value.
     *
     * @property instance the name of the cache.
     * @property key the shown key, or null when the cache does not record keys.
     * @since 1.0.0
     */
    public class Hit internal constructor(
        override val instance: InstanceName,
        public val key: String?,
    ) : CacheEvent {
        /**
         * Shows the cache and the key.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Hit($instance${keyPart(key)})"
    }

    /**
     * A lookup found no live value.
     *
     * @property instance the name of the cache.
     * @property key the shown key, or null when the cache does not record keys.
     * @since 1.0.0
     */
    public class Miss internal constructor(
        override val instance: InstanceName,
        public val key: String?,
    ) : CacheEvent {
        /**
         * Shows the cache and the key.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Miss($instance${keyPart(key)})"
    }

    /**
     * A loader returned a value.
     *
     * @property instance the name of the cache.
     * @property key the shown key, or null when the cache does not record keys.
     * @property duration how long the load took.
     * @since 1.0.0
     */
    public class Loaded internal constructor(
        override val instance: InstanceName,
        public val key: String?,
        public val duration: Duration,
    ) : CacheEvent {
        /**
         * Shows the cache, the key and the duration.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Loaded($instance${keyPart(key)}, $duration)"
    }

    /**
     * A load failed.
     *
     * @property instance the name of the cache.
     * @property key the shown key, or null when the cache does not record keys.
     * @property failureType the simple name of the type of the failure, never its message.
     * @since 1.0.0
     */
    public class LoadFailed internal constructor(
        override val instance: InstanceName,
        public val key: String?,
        public val failureType: String,
    ) : CacheEvent {
        /**
         * Shows the cache, the key and the type of the failure.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "LoadFailed($instance${keyPart(key)}, $failureType)"
    }

    /**
     * A write evicted [count] entries.
     *
     * @property instance the name of the cache.
     * @property reason why the entries were evicted.
     * @property count how many entries were evicted.
     * @property keys the shown keys of at most the first 64 evicted entries, or empty when the cache does not record
     *   keys.
     * @since 1.0.0
     */
    public class Evicted internal constructor(
        override val instance: InstanceName,
        public val reason: EvictionReason,
        public val count: Int,
        public val keys: List<String>,
    ) : CacheEvent {
        /**
         * Shows the cache, the reason, the count and the keys.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Evicted($instance, ${reason.id}, $count${keysPart(keys)})"
    }

    /**
     * Expired entries were removed, or a write was not stored because its lifetime was over at once.
     *
     * @property instance the name of the cache.
     * @property count how many entries expired.
     * @property keys the shown keys of at most the first 64 expired entries, or empty when the cache does not record
     *   keys.
     * @since 1.0.0
     */
    public class Expired internal constructor(
        override val instance: InstanceName,
        public val count: Int,
        public val keys: List<String>,
    ) : CacheEvent {
        /**
         * Shows the cache, the count and the keys.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Expired($instance, $count${keysPart(keys)})"
    }

    /**
     * A load failed and the expired value of the key was served instead.
     *
     * @property instance the name of the cache.
     * @property key the shown key, or null when the cache does not record keys.
     * @since 1.0.0
     */
    public class StaleServed internal constructor(
        override val instance: InstanceName,
        public val key: String?,
    ) : CacheEvent {
        /**
         * Shows the cache and the key.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "StaleServed($instance${keyPart(key)})"
    }

    /**
     * The cache was cleared.
     *
     * @property instance the name of the cache.
     * @property entries how many entries the cache held before.
     * @since 1.0.0
     */
    public class Cleared internal constructor(
        override val instance: InstanceName,
        public val entries: Int,
    ) : CacheEvent {
        /**
         * Shows the cache and the number of entries.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Cleared($instance, $entries)"
    }

    /**
     * The topic of the events of every cache.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The topic `kontinuity.cache`, declared on the event bus of the environment by the first cache.
         *
         * @since 1.0.0
         */
        public val TOPIC: TopicKey<CacheEvent> = TopicKey("kontinuity.cache")

        private fun keyPart(key: String?): String = key?.let { shown -> ", key=$shown" }.orEmpty()

        private fun keysPart(keys: List<String>): String {
            if (keys.isEmpty()) return ""
            return keys.joinToString(prefix = ", keys=[", postfix = "]")
        }
    }
}
