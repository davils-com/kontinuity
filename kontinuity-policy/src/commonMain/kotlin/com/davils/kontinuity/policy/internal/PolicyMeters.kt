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

package com.davils.kontinuity.policy.internal

import com.davils.kollect.core.Family
import com.davils.kollect.core.MeterRegistry
import com.davils.kollect.core.meter.Counter
import com.davils.kollect.core.meter.Timer
import com.davils.kontinuity.core.InstanceName
import kotlin.time.Duration

private const val NAME: String = "name"
private const val KIND: String = "kind"

internal class PolicyMeters(registry: MeterRegistry, name: InstanceName) {
    private val label: String = name.value
    private val calls: Family<Counter> = callsOf(registry)
    private val durations: Family<Timer> = durationsOf(registry)

    fun call(outcome: PolicyOutcome, duration: Duration?) {
        val counter = calls.with(label, outcome.kind)
        counter.increment()
        if (duration == null) return
        val timer = durations.with(label, outcome.kind)
        timer.record(duration)
    }

    private companion object {
        fun callsOf(registry: MeterRegistry): Family<Counter> {
            val registration = registry.counter("kontinuity.policy.calls") {
                description = "The calls through a policy by how they ended."
                labelKeys(NAME, KIND)
            }
            return registration.family
        }

        fun durationsOf(registry: MeterRegistry): Family<Timer> {
            val registration = registry.timer("kontinuity.policy.call.duration") {
                description = "How long the calls through a policy took, every stage included."
                labelKeys(NAME, KIND)
            }
            return registration.family
        }
    }
}
