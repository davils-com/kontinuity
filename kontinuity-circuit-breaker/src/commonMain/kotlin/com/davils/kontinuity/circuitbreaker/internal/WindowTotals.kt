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

private const val PERCENT: Double = 100.0
private const val UNKNOWN: Double = -1.0

internal class WindowTotals(val calls: Int, val failures: Int, val slow: Int, val slowFailures: Int) {
    fun failureRate(minimum: Int): Double = rateOf(failures, minimum)

    fun slowCallRate(minimum: Int): Double = rateOf(slow, minimum)

    private fun rateOf(count: Int, minimum: Int): Double {
        if (calls < minimum) return UNKNOWN
        return count * PERCENT / calls
    }

    companion object {
        val EMPTY: WindowTotals = WindowTotals(calls = 0, failures = 0, slow = 0, slowFailures = 0)
    }
}
