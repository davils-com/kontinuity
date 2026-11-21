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

package com.davils.kontinuity.circuitbreaker.internal

import com.davils.kollect.core.Family
import com.davils.kollect.core.MeterRegistry
import com.davils.kollect.core.meter.Counter
import com.davils.kollect.core.meter.Gauge
import com.davils.kollect.core.meter.MeasureUnit
import com.davils.kollect.core.meter.StateSet
import com.davils.kollect.core.meter.Timer
import com.davils.kontinuity.circuitbreaker.CircuitState
import com.davils.kontinuity.core.InstanceName
import kotlin.time.Duration

private const val NAME: String = "name"
private const val KIND: String = "kind"
private const val FROM: String = "from"
private const val TO: String = "to"

internal class BreakerMeters(registry: MeterRegistry, name: InstanceName) {
    private val label: String = name.value
    private val calls: Family<Counter> = callsOf(registry)
    private val durations: Family<Timer> = durationsOf(registry)
    private val transitions: Family<Counter> = transitionsOf(registry)
    private val state: StateSet<CircuitState> = stateOf(registry).with(label)
    private val failureRate: Gauge = rateOf(
        registry = registry,
        metric = FAILURE_RATE,
        text = FAILURE_RATE_TEXT,
    ).with(label)
    private val slowCallRate: Gauge = rateOf(
        registry = registry,
        metric = SLOW_CALL_RATE,
        text = SLOW_CALL_RATE_TEXT,
    ).with(label)
    private val bufferedCalls: Gauge = bufferedOf(registry).with(label)

    fun call(kind: String, duration: Duration?) {
        val counter = calls.with(label, kind)
        counter.increment()
        if (duration == null) return
        val timer = durations.with(label, kind)
        timer.record(duration)
    }

    fun transition(from: CircuitState, to: CircuitState) {
        val counter = transitions.with(label, from.name, to.name)
        counter.increment()
        state.set(to)
    }

    fun window(failureRate: Double, slowCallRate: Double, bufferedCalls: Int) {
        this.failureRate.set(failureRate)
        this.slowCallRate.set(slowCallRate)
        this.bufferedCalls.set(bufferedCalls.toDouble())
    }

    companion object {
        const val SUCCESSFUL: String = "successful"
        const val FAILED: String = "failed"
        const val IGNORED: String = "ignored"
        const val RELEASED: String = "released"
        const val NOT_PERMITTED: String = "not_permitted"
        private const val STATE: String = "kontinuity.circuit_breaker.state"
        private const val FAILURE_RATE: String = "kontinuity.circuit_breaker.failure_rate"
        private const val FAILURE_RATE_TEXT: String = "The failure rate of the window of a circuit breaker."
        private const val SLOW_CALL_RATE: String = "kontinuity.circuit_breaker.slow_call_rate"
        private const val SLOW_CALL_RATE_TEXT: String = "The rate of slow calls of the window of a circuit breaker."

        private fun callsOf(registry: MeterRegistry): Family<Counter> {
            val registration = registry.counter("kontinuity.circuit_breaker.calls") {
                description = "The calls of a circuit breaker by how they ended."
                labelKeys(NAME, KIND)
            }
            return registration.family
        }

        private fun durationsOf(registry: MeterRegistry): Family<Timer> {
            val registration = registry.timer("kontinuity.circuit_breaker.call.duration") {
                description = "How long the recorded calls of a circuit breaker took."
                labelKeys(NAME, KIND)
            }
            return registration.family
        }

        private fun transitionsOf(registry: MeterRegistry): Family<Counter> {
            val registration = registry.counter("kontinuity.circuit_breaker.transitions") {
                description = "The state transitions of a circuit breaker."
                labelKeys(NAME, FROM, TO)
            }
            return registration.family
        }

        private fun stateOf(registry: MeterRegistry): Family<StateSet<CircuitState>> {
            val registration = registry.stateSet(
                name = STATE,
                initial = CircuitState.Closed,
            ) {
                description = "The state of a circuit breaker."
                labelKeys(NAME)
            }
            return registration.family
        }

        private fun rateOf(registry: MeterRegistry, metric: String, text: String): Family<Gauge> {
            val registration = registry.gauge(metric) {
                description = "$text It is -1 while the window holds too few calls."
                unit = MeasureUnit.PERCENT
                labelKeys(NAME)
            }
            return registration.family
        }

        private fun bufferedOf(registry: MeterRegistry): Family<Gauge> {
            val registration = registry.gauge("kontinuity.circuit_breaker.buffered_calls") {
                description = "The calls in the window of a circuit breaker."
                labelKeys(NAME)
            }
            return registration.family
        }
    }
}
