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

import com.davils.kollect.core.Family
import com.davils.kollect.core.MeterRegistry
import com.davils.kollect.core.meter.Counter
import com.davils.kollect.core.meter.Gauge
import com.davils.kollect.core.meter.Timer
import com.davils.kontinuity.cache.EvictionReason
import com.davils.kontinuity.core.InstanceName
import kotlin.time.Duration

private const val NAME: String = "name"
private const val RESULT: String = "result"
private const val REASON: String = "reason"

internal class CacheMeters(registry: MeterRegistry, name: InstanceName) {
    private val label: String = name.value
    private val gets: Family<Counter> = counterOf(
        registry = registry,
        metric = "kontinuity.cache.gets",
        text = "The lookups of a cache by whether they found a live value.",
        key = RESULT,
    )
    private val loads: Family<Counter> = counterOf(
        registry = registry,
        metric = "kontinuity.cache.loads",
        text = "The loads of a cache by how they ended.",
        key = RESULT,
    )
    private val evictions: Family<Counter> = counterOf(
        registry = registry,
        metric = "kontinuity.cache.evictions",
        text = "The entries a cache evicted, by the bound they exceeded.",
        key = REASON,
    )
    private val duration: Timer = durationOf(registry).with(label)
    private val size: Gauge = gaugeOf(
        registry = registry,
        metric = "kontinuity.cache.size",
        text = "The entries a cache holds, the expired ones not yet removed included.",
    ).with(label)
    private val weight: Gauge = gaugeOf(
        registry = registry,
        metric = "kontinuity.cache.weight",
        text = "The weight of the entries a cache holds.",
    ).with(label)

    fun lookedUp(isHit: Boolean) {
        val result = HIT.takeIf { isHit } ?: MISS
        gets.with(label, result).increment()
    }

    fun loaded(isSuccess: Boolean, elapsed: Duration) {
        val result = SUCCESS.takeIf { isSuccess } ?: FAILURE
        loads.with(label, result).increment()
        duration.record(elapsed)
    }

    fun evicted(reason: EvictionReason, count: Int) {
        evictions.with(label, reason.id).add(count.toLong())
    }

    fun resized(entries: Int, total: Long) {
        size.set(entries.toDouble())
        weight.set(total.toDouble())
    }

    private companion object {
        const val HIT: String = "hit"
        const val MISS: String = "miss"
        const val SUCCESS: String = "success"
        const val FAILURE: String = "failure"

        fun counterOf(registry: MeterRegistry, metric: String, text: String, key: String): Family<Counter> {
            val registration = registry.counter(metric) {
                description = text
                labelKeys(NAME, key)
            }
            return registration.family
        }

        fun gaugeOf(registry: MeterRegistry, metric: String, text: String): Family<Gauge> {
            val registration = registry.gauge(metric) {
                description = text
                labelKeys(NAME)
            }
            return registration.family
        }

        fun durationOf(registry: MeterRegistry): Family<Timer> {
            val registration = registry.timer("kontinuity.cache.load.duration") {
                description = "How long the loads of a cache took."
                labelKeys(NAME)
            }
            return registration.family
        }
    }
}
