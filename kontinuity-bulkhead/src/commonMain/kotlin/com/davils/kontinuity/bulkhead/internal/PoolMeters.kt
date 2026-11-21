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

package com.davils.kontinuity.bulkhead.internal

import com.davils.kollect.core.Family
import com.davils.kollect.core.MeterRegistry
import com.davils.kollect.core.meter.Counter
import com.davils.kollect.core.meter.Gauge
import com.davils.kollect.core.meter.Timer
import com.davils.kontinuity.core.InstanceName
import kotlin.time.Duration

private const val NAME: String = "name"
private const val KIND: String = "kind"

internal class PoolMeters(registry: MeterRegistry, name: InstanceName, scheme: MeterScheme) {
    private val label: String = name.value
    private val calls: Family<Counter> = callsOf(registry, scheme)
    private val waits: Timer = waitsOf(registry, scheme).with(label)
    private val first: Gauge = gaugeOf(
        registry = registry,
        metric = "${scheme.prefix}.${scheme.firstGauge}",
        text = scheme.firstText,
    ).with(label)
    private val second: Gauge = gaugeOf(
        registry = registry,
        metric = "${scheme.prefix}.${scheme.secondGauge}",
        text = scheme.secondText,
    ).with(label)

    fun call(kind: String) {
        val counter = calls.with(label, kind)
        counter.increment()
    }

    fun waited(duration: Duration) {
        waits.record(duration)
    }

    fun publish(source: LevelSource) {
        var levels = source.levels()
        show(levels)
        while (source.version != levels.version) {
            levels = source.levels()
            show(levels)
        }
    }

    private fun show(levels: Levels) {
        first.set(levels.first.toDouble())
        second.set(levels.second.toDouble())
    }

    companion object {
        const val PERMITTED: String = "permitted"
        const val REJECTED: String = "rejected"
        const val FINISHED: String = "finished"
        const val CANCELLED: String = "cancelled"

        private fun callsOf(registry: MeterRegistry, scheme: MeterScheme): Family<Counter> {
            val registration = registry.counter("${scheme.prefix}.calls") {
                description = "The calls of ${scheme.component} by how they were decided and ended."
                labelKeys(NAME, KIND)
            }
            return registration.family
        }

        private fun waitsOf(registry: MeterRegistry, scheme: MeterScheme): Family<Timer> {
            val registration = registry.timer("${scheme.prefix}.wait") {
                description = "How long the permitted calls of ${scheme.component} waited for their turn."
                labelKeys(NAME)
            }
            return registration.family
        }

        private fun gaugeOf(registry: MeterRegistry, metric: String, text: String): Family<Gauge> {
            val registration = registry.gauge(metric) {
                description = text
                labelKeys(NAME)
            }
            return registration.family
        }
    }
}
