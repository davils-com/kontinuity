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

import com.davils.kontinuity.circuitbreaker.CircuitState
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration

internal sealed interface Phase {
    val state: CircuitState
    val window: CallWindow?

    data class Closed(override val window: CallWindow) : Phase {
        override val state: CircuitState get() = CircuitState.Closed
    }

    data class MetricsOnly(override val window: CallWindow) : Phase {
        override val state: CircuitState get() = CircuitState.MetricsOnly
    }

    data class Open(
        val openings: Int,
        val wait: Duration,
        val until: ComparableTimeMark,
        override val window: CallWindow?,
    ) : Phase {
        override val state: CircuitState get() = CircuitState.Open
    }

    class HalfOpen(
        val openings: Int,
        val wait: Duration,
        val trials: Int,
        val deadline: ComparableTimeMark?,
    ) : Phase {
        override val state: CircuitState get() = CircuitState.HalfOpen
        override val window: CountWindow = CountWindow(trials)
        private var free: Int = trials
        private var recorded: Int = 0

        val isComplete: Boolean get() = recorded >= trials

        val isExpired: Boolean get() = deadline?.hasPassedNow() == true

        fun take(): Boolean {
            if (free == 0) return false
            free -= 1
            return true
        }

        fun giveBack() {
            free += 1
        }

        fun record(outcome: CallOutcome) {
            window.record(outcome)
            recorded += 1
        }
    }

    data object Disabled : Phase {
        override val state: CircuitState get() = CircuitState.Disabled
        override val window: CallWindow? get() = null
    }

    data object ForcedOpen : Phase {
        override val state: CircuitState get() = CircuitState.ForcedOpen
        override val window: CallWindow? get() = null
    }
}
