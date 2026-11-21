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

import com.davils.kontinuity.circuitbreaker.CircuitBreakerConfig
import com.davils.kontinuity.core.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

internal class TransitionTimer(private val config: CircuitBreakerConfig, environment: Environment) {
    private val scope: CoroutineScope? = environment.scope.takeIf { config.isAutomaticTransitionEnabled }
    private var job: Job? = null

    fun reschedule(next: Phase, token: Long, wake: (Long) -> Unit) {
        job?.cancel()
        job = scheduled(delayOf(next), token, wake)
    }

    private fun scheduled(wait: Duration?, token: Long, wake: (Long) -> Unit): Job? {
        val timerScope = scope ?: return null
        return wait?.let { delay ->
            timerScope.launch {
                delay(delay)
                wake(token)
            }
        }
    }

    private fun delayOf(next: Phase): Duration? = when (next) {
        is Phase.Open -> next.wait
        is Phase.HalfOpen -> config.maxWaitInHalfOpenState.takeIf { next.deadline != null }
        is Phase.Closed, is Phase.MetricsOnly, Phase.Disabled, Phase.ForcedOpen -> null
    }
}
