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
import com.davils.kollect.core.meter.Timer
import com.davils.kontinuity.core.InstanceName
import kotlin.time.Duration

private const val NAME: String = "name"
private const val KIND: String = "kind"

internal class LimiterMeters(registry: MeterRegistry, name: InstanceName) {
    private val label: String = name.value
    private val calls: Family<Counter> = callsOf(registry)
    private val waits: Timer = waitsOf(registry).with(label)
    private val available: Gauge = gaugeOf(
        registry = registry,
        metric = "kontinuity.rate_limiter.available_permissions",
        text = "The permits a caller of a rate limiter could take at once.",
    ).with(label)
    private val waiting: Gauge = gaugeOf(
        registry = registry,
        metric = "kontinuity.rate_limiter.waiting_callers",
        text = "The callers that wait for the permits of a rate limiter.",
    ).with(label)

    fun call(kind: String) {
        val counter = calls.with(label, kind)
        counter.increment()
    }

    fun waited(duration: Duration) {
        waits.record(duration)
    }

    fun levels(availablePermissions: Int, waitingCallers: Int) {
        available.set(availablePermissions.toDouble())
        waiting.set(waitingCallers.toDouble())
    }

    companion object {
        const val ACQUIRED: String = "acquired"
        const val REJECTED: String = "rejected"
        const val CANCELLED: String = "cancelled"

        private fun callsOf(registry: MeterRegistry): Family<Counter> {
            val registration = registry.counter("kontinuity.rate_limiter.calls") {
                description = "The requests for the permits of a rate limiter by how they ended."
                labelKeys(NAME, KIND)
            }
            return registration.family
        }

        private fun waitsOf(registry: MeterRegistry): Family<Timer> {
            val registration = registry.timer("kontinuity.rate_limiter.wait") {
                description = "How long the callers of a rate limiter waited for their permits."
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
