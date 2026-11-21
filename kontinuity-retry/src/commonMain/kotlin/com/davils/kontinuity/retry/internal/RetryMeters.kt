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

package com.davils.kontinuity.retry.internal

import com.davils.kollect.core.Family
import com.davils.kollect.core.MeterRegistry
import com.davils.kollect.core.meter.Counter
import com.davils.kollect.core.meter.Timer
import com.davils.kontinuity.core.InstanceName
import kotlin.time.Duration

private const val NAME: String = "name"
private const val KIND: String = "kind"

internal class RetryMeters(registry: MeterRegistry, name: InstanceName) {
    private val label: String = name.value
    private val calls: Family<Counter> = callsOf(registry)
    private val retries: Counter = retriesOf(registry).with(label)
    private val backoff: Timer = backoffOf(registry).with(label)

    fun call(kind: CallKind) {
        val counter = calls.with(label, kind.label)
        counter.increment()
    }

    fun retry(delay: Duration) {
        retries.increment()
        backoff.record(delay)
    }

    private companion object {
        fun callsOf(registry: MeterRegistry): Family<Counter> {
            val registration = registry.counter("kontinuity.retry.calls") {
                description = "The calls of a retry by how they ended."
                labelKeys(NAME, KIND)
            }
            return registration.family
        }

        fun retriesOf(registry: MeterRegistry): Family<Counter> {
            val registration = registry.counter("kontinuity.retry.retries") {
                description = "The retries of a retry, the first attempts not included."
                labelKeys(NAME)
            }
            return registration.family
        }

        fun backoffOf(registry: MeterRegistry): Family<Timer> {
            val registration = registry.timer("kontinuity.retry.backoff") {
                description = "The waits a retry planned before its retries."
                labelKeys(NAME)
            }
            return registration.family
        }
    }
}
