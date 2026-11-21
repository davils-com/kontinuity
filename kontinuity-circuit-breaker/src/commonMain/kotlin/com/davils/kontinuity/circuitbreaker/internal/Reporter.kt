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

package com.davils.kontinuity.circuitbreaker.internal

import com.davils.kontinuity.circuitbreaker.CircuitBreakerEvent
import com.davils.kontinuity.circuitbreaker.CircuitState
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kore.core.annotation.DavilsInternal

internal class Reporter(
    private val name: InstanceName,
    private val sink: EventSink<CircuitBreakerEvent>,
    private val meters: BreakerMeters?,
) {
    fun settled(settlement: Settlement) {
        when (settlement) {
            is Settlement.Recorded -> {
                recorded(settlement)
            }

            is Settlement.Ignored -> {
                meters?.call(BreakerMeters.IGNORED, settlement.duration)
                sink.emit { CircuitBreakerEvent.Ignored(name, settlement.duration) }
            }

            Settlement.Released -> {
                meters?.call(BreakerMeters.RELEASED, duration = null)
                sink.emit { CircuitBreakerEvent.Released(name) }
            }
        }
    }

    fun notPermitted(state: CircuitState) {
        meters?.call(BreakerMeters.NOT_PERMITTED, duration = null)
        sink.emit { CircuitBreakerEvent.NotPermitted(name, state) }
    }

    fun changed(changes: Changes) {
        changes.forEachFailureRate { rate -> sink.emit { CircuitBreakerEvent.FailureRateExceeded(name, rate) } }
        changes.forEachSlowCallRate { rate -> sink.emit { CircuitBreakerEvent.SlowCallRateExceeded(name, rate) } }
        changes.forEachTransition { from, to ->
            meters?.transition(from, to)
            sink.emit { CircuitBreakerEvent.StateChanged(name, from, to) }
        }
        changes.ifReset { sink.emit { CircuitBreakerEvent.Reset(name) } }
    }

    fun window(failureRate: Double, slowCallRate: Double, bufferedCalls: Int) {
        meters?.window(failureRate = failureRate, slowCallRate = slowCallRate, bufferedCalls = bufferedCalls)
    }

    private fun recorded(settlement: Settlement.Recorded) {
        val outcome = settlement.outcome
        val isFailure = outcome.isFailure
        meters?.call(kindOf(isFailure), settlement.duration)
        sink.emit { eventOf(settlement) }
    }

    private fun eventOf(settlement: Settlement.Recorded): CircuitBreakerEvent {
        val outcome = settlement.outcome
        if (!outcome.isFailure) return CircuitBreakerEvent.Succeeded(name, settlement.duration, outcome.isSlow)
        return CircuitBreakerEvent.Failed(
            instance = name,
            duration = settlement.duration,
            isSlow = outcome.isSlow,
            failureType = settlement.failureType,
        )
    }

    private fun kindOf(isFailure: Boolean): String {
        if (isFailure) return BreakerMeters.FAILED
        return BreakerMeters.SUCCESSFUL
    }
}
