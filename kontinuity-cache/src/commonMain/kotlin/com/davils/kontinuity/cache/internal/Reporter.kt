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

import com.davils.kontinuity.cache.CacheEvent
import com.davils.kontinuity.cache.EvictionReason
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.failure.contained
import com.davils.kore.core.text.SafeText
import kotlin.time.Duration

internal class Reporter<K : Any>(
    private val name: InstanceName,
    private val sink: EventSink<CacheEvent>,
    private val meters: CacheMeters?,
    private val isRecordingKeys: Boolean,
) {
    fun lookedUp(key: K, isHit: Boolean) {
        meters?.lookedUp(isHit)
        sink.emit { lookupOf(key, isHit) }
    }

    fun loaded(key: K, elapsed: Duration) {
        meters?.loaded(isSuccess = true, elapsed = elapsed)
        sink.emit { CacheEvent.Loaded(instance = name, key = shown(key), duration = elapsed) }
    }

    fun loadFailed(key: K, elapsed: Duration, failure: Throwable) {
        meters?.loaded(isSuccess = false, elapsed = elapsed)
        val type = failure::class.simpleName ?: UNNAMED
        sink.emit { CacheEvent.LoadFailed(instance = name, key = shown(key), failureType = type) }
    }

    fun staleServed(key: K) {
        sink.emit { CacheEvent.StaleServed(name, shown(key)) }
    }

    fun changed(changes: Changes<K>) {
        evicted(EvictionReason.Size, changes.evictedBySize)
        evicted(EvictionReason.Weight, changes.evictedByWeight)
        expired(changes.expired)
        changes.cleared?.let { entries -> sink.emit { CacheEvent.Cleared(name, entries) } }
        if (changes.isResized) meters?.resized(changes.size, changes.weight)
    }

    private fun evicted(reason: EvictionReason, removals: Removals<K>) {
        val count = removals.count
        if (count == 0) return
        meters?.evicted(reason, count)
        sink.emit { CacheEvent.Evicted(instance = name, reason = reason, count = count, keys = shownAll(removals)) }
    }

    private fun expired(removals: Removals<K>) {
        val count = removals.count
        if (count == 0) return
        sink.emit { CacheEvent.Expired(instance = name, count = count, keys = shownAll(removals)) }
    }

    private fun lookupOf(key: K, isHit: Boolean): CacheEvent {
        val text = shown(key)
        if (isHit) return CacheEvent.Hit(name, text)
        return CacheEvent.Miss(name, text)
    }

    private fun shownAll(removals: Removals<K>): List<String> = removals.keys.map(::textOf)

    private fun shown(key: K): String? = key.takeIf { isRecordingKeys }?.let(::textOf)

    private fun textOf(key: K): String {
        val text = contained { key.toString() }.getOrElse { UNSHOWABLE }
        return SafeText.text(text)
    }

    private companion object {
        const val UNNAMED: String = "Throwable"
        const val UNSHOWABLE: String = "<unshowable>"
    }
}
