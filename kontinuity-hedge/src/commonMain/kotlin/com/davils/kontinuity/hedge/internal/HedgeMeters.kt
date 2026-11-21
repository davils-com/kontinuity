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

package com.davils.kontinuity.hedge.internal

import com.davils.kollect.core.Family
import com.davils.kollect.core.MeterRegistry
import com.davils.kollect.core.meter.Counter
import com.davils.kollect.core.meter.Gauge
import com.davils.kollect.core.meter.MeasureUnit
import com.davils.kollect.core.meter.Timer
import com.davils.kontinuity.core.InstanceName
import kotlin.time.Duration
import kotlin.time.DurationUnit

private const val NAME: String = "name"
private const val KIND: String = "kind"

internal class HedgeMeters(registry: MeterRegistry, name: InstanceName) {
    private val label: String = name.value
    private val calls: Family<Counter> = callsOf(registry)
    private val hedges: Counter = hedgesOf(registry).with(label)
    private val durations: Timer = durationsOf(registry).with(label)
    private val delay: Gauge = delayOf(registry).with(label)

    fun call(kind: String, duration: Duration?) {
        val counter = calls.with(label, kind)
        counter.increment()
        if (duration == null) return
        durations.record(duration)
    }

    fun hedge() {
        hedges.increment()
    }

    fun delay(value: Duration) {
        delay.set(value.toDouble(DurationUnit.SECONDS))
    }

    companion object {
        const val PRIMARY_SUCCEEDED: String = "primary_succeeded"
        const val HEDGE_SUCCEEDED: String = "hedge_succeeded"
        const val FAILED: String = "failed"
        const val CANCELLED: String = "cancelled"

        private fun callsOf(registry: MeterRegistry): Family<Counter> {
            val registration = registry.counter("kontinuity.hedge.calls") {
                description = "The calls of a hedge by how they ended."
                labelKeys(NAME, KIND)
            }
            return registration.family
        }

        private fun hedgesOf(registry: MeterRegistry): Family<Counter> {
            val registration = registry.counter("kontinuity.hedge.hedges") {
                description = "The hedged copies a hedge started."
                labelKeys(NAME)
            }
            return registration.family
        }

        private fun durationsOf(registry: MeterRegistry): Family<Timer> {
            val registration = registry.timer("kontinuity.hedge.call.duration") {
                description = "How long the calls of a hedge took that ended with a success or a failure."
                labelKeys(NAME)
            }
            return registration.family
        }

        private fun delayOf(registry: MeterRegistry): Family<Gauge> {
            val registration = registry.gauge("kontinuity.hedge.delay") {
                description = "The delay a hedge waits before it starts its first hedged copy."
                unit = MeasureUnit.SECONDS
                labelKeys(NAME)
            }
            return registration.family
        }
    }
}
