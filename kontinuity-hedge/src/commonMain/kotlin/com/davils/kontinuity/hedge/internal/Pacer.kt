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

package com.davils.kontinuity.hedge.internal

import com.davils.kontinuity.hedge.HedgeDelay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

internal sealed interface Pacer {
    val current: Duration

    fun observe(latency: Duration)

    class Fixed(delay: HedgeDelay.Fixed) : Pacer {
        override val current: Duration = delay.duration

        override fun observe(latency: Duration): Unit = Unit
    }

    class Percentile(private val delay: HedgeDelay.Percentile) : Pacer {
        private val window: LatencyWindow = LatencyWindow(delay.window)

        override var current: Duration = delay.fallback
            private set

        override fun observe(latency: Duration) {
            window.add(latency.inWholeNanoseconds)
            if (window.size < delay.minimumSamples) return
            val nanos = window.percentile(delay.percentile)
            current = nanos.nanoseconds.coerceIn(
                minimumValue = HedgeDelay.MINIMUM_DELAY,
                maximumValue = HedgeDelay.HARD_DELAY,
            )
        }
    }

    companion object {
        fun of(delay: HedgeDelay): Pacer = when (delay) {
            is HedgeDelay.Fixed -> Fixed(delay)
            is HedgeDelay.Percentile -> Percentile(delay)
        }
    }
}
