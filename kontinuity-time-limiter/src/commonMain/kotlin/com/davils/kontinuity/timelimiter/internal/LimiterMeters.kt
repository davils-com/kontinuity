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

package com.davils.kontinuity.timelimiter.internal

import com.davils.kollect.core.Family
import com.davils.kollect.core.MeterRegistry
import com.davils.kollect.core.meter.Counter
import com.davils.kollect.core.meter.Gauge
import com.davils.kollect.core.meter.Timer
import com.davils.kontinuity.core.InstanceName
import kotlin.time.Duration

private const val NAME: String = "name"
private const val KIND: String = "kind"

internal class LimiterMeters(registry: MeterRegistry, name: InstanceName) {
    private val label: String = name.value
    private val calls: Family<Counter> = callsOf(registry)
    private val duration: Timer = durationsOf(registry).with(label)
    private val detached: Gauge = detachedOf(registry).with(label)

    fun call(outcome: CallOutcome, elapsed: Duration?) {
        val counter = calls.with(label, outcome.label)
        counter.increment()
        if (elapsed == null) return
        duration.record(elapsed)
    }

    fun detached(inFlight: Int) {
        detached.set(inFlight.toDouble())
    }

    private companion object {
        fun callsOf(registry: MeterRegistry): Family<Counter> {
            val registration = registry.counter("kontinuity.time_limiter.calls") {
                description = "The calls of a time limiter by how they ended."
                labelKeys(NAME, KIND)
            }
            return registration.family
        }

        fun durationsOf(registry: MeterRegistry): Family<Timer> {
            val registration = registry.timer("kontinuity.time_limiter.call.duration") {
                description = "How long the callers of a time limiter waited for their calls."
                labelKeys(NAME)
            }
            return registration.family
        }

        fun detachedOf(registry: MeterRegistry): Family<Gauge> {
            val registration = registry.gauge("kontinuity.time_limiter.detached") {
                description = "The calls of a soft time limiter whose detached work runs."
                labelKeys(NAME)
            }
            return registration.family
        }
    }
}
