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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

internal class Reservation private constructor(
    val permits: Int,
    val waitNanos: Long,
    val isGranted: Boolean,
    private val giveBack: (Long) -> Boolean,
) {
    val wait: Duration get() = waitNanos.nanoseconds

    val isWaiting: Boolean get() = isGranted && waitNanos > 0L

    fun refund(nowNanos: Long): Boolean = giveBack(nowNanos)

    companion object {
        private val kept: (Long) -> Boolean = { _ -> false }

        fun refused(permits: Int, waitNanos: Long): Reservation =
            Reservation(permits = permits, waitNanos = waitNanos, isGranted = false, giveBack = kept)

        fun granted(permits: Int, waitNanos: Long, giveBack: (Long) -> Boolean): Reservation =
            Reservation(permits = permits, waitNanos = waitNanos, isGranted = true, giveBack = giveBack)
    }
}
