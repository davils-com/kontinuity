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

import com.davils.kontinuity.circuitbreaker.CircuitBreakerConfig
import com.davils.kontinuity.circuitbreaker.CircuitState
import com.davils.kontinuity.circuitbreaker.SlidingWindow
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.backoff.delayBefore
import com.davils.kore.core.annotation.DavilsInternal
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration

internal class PhaseFactory(private val config: CircuitBreakerConfig, private val environment: Environment) {
    fun closed(): Phase = Phase.Closed(newWindow())

    fun open(openings: Int, previousWait: Duration, window: CallWindow?): Phase.Open {
        val wait = config.waitInOpenState.delayBefore(
            attempt = openings + 1,
            previous = previousWait,
            entropy = environment.entropy,
        )
        return reopen(openings = openings, wait = wait, window = window)
    }

    fun reopen(openings: Int, wait: Duration, window: CallWindow?): Phase.Open = Phase.Open(
        openings = openings,
        wait = wait,
        until = environment.timeSource.markNow() + wait,
        window = window,
    )

    fun halfOpen(openings: Int, wait: Duration): Phase.HalfOpen {
        val maxWait = config.maxWaitInHalfOpenState
        val deadline: ComparableTimeMark? = (environment.timeSource.markNow() + maxWait).takeIf { maxWait.isFinite() }
        return Phase.HalfOpen(
            openings = openings,
            wait = wait,
            trials = config.permittedCallsInHalfOpenState,
            deadline = deadline,
        )
    }

    fun forState(target: CircuitState, window: CallWindow?): Phase = when (target) {
        CircuitState.Closed -> closed()
        CircuitState.MetricsOnly -> Phase.MetricsOnly(newWindow())
        CircuitState.Disabled -> Phase.Disabled
        CircuitState.ForcedOpen -> Phase.ForcedOpen
        CircuitState.Open -> open(openings = 1, previousWait = Duration.ZERO, window = window)
        CircuitState.HalfOpen -> halfOpen(openings = 1, wait = firstWait())
    }

    fun afterHalfOpenTimeout(trial: Phase.HalfOpen): Phase {
        if (config.stateAfterHalfOpenTimeout == CircuitState.Closed) return closed()
        return reopen(openings = trial.openings, wait = trial.wait, window = trial.window)
    }

    private fun firstWait(): Duration = config.waitInOpenState.delayBefore(
        attempt = 2,
        previous = Duration.ZERO,
        entropy = environment.entropy,
    )

    private fun newWindow(): CallWindow = when (val window = config.slidingWindow) {
        is SlidingWindow.CountBased -> CountWindow(window.size)
        is SlidingWindow.TimeBased -> TimeWindow(window.length, window.buckets, environment.timeSource)
    }
}
