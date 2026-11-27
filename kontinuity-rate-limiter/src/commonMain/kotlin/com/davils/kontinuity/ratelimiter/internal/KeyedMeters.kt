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

package com.davils.kontinuity.ratelimiter.internal

import com.davils.kollect.core.Family
import com.davils.kollect.core.MeterRegistry
import com.davils.kollect.core.meter.Counter
import com.davils.kollect.core.meter.Gauge
import com.davils.kontinuity.core.InstanceName

private const val NAME: String = "name"
private const val OUTCOME: String = "outcome"
private const val PERMITTED: String = "permitted"
private const val REJECTED: String = "rejected"

internal class KeyedMeters(registry: MeterRegistry, name: InstanceName) {
    private val label: String = name.value
    private val calls: Family<Counter> = callsOf(registry)
    private val keys: Gauge = keysOf(registry).with(label)

    fun counted(isPermitted: Boolean) {
        val outcome = PERMITTED.takeIf { isPermitted } ?: REJECTED
        count(outcome)
    }

    fun overflowed() {
        count("overflowed")
    }

    fun keys(count: Int) {
        keys.set(count.toDouble())
    }

    private fun count(outcome: String) {
        val counter = calls.with(label, outcome)
        counter.increment()
    }

    private companion object {
        fun callsOf(registry: MeterRegistry): Family<Counter> {
            val registration = registry.counter("kontinuity.keyed_rate_limiter.calls") {
                description = "The calls of a keyed rate limiter by how they ended."
                labelKeys(NAME, OUTCOME)
            }
            return registration.family
        }

        fun keysOf(registry: MeterRegistry): Family<Gauge> {
            val registration = registry.gauge("kontinuity.keyed_rate_limiter.keys") {
                description = "The keys a keyed rate limiter holds."
                labelKeys(NAME)
            }
            return registration.family
        }
    }
}
