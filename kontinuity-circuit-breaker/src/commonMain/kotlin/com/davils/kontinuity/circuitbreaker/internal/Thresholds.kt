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

internal class Thresholds(private val config: CircuitBreakerConfig) {
    fun isOver(totals: WindowTotals, minimum: Int): Boolean =
        totals.failureRate(minimum) >= config.failureRateThreshold ||
            totals.slowCallRate(minimum) >= config.slowCallRateThreshold

    fun report(totals: WindowTotals, minimum: Int, changes: Changes): Boolean {
        val failureRate = totals.failureRate(minimum)
        val slowCallRate = totals.slowCallRate(minimum)
        val isFailing = failureRate >= config.failureRateThreshold
        val isSlow = slowCallRate >= config.slowCallRateThreshold
        if (isFailing) changes.failureRateExceeded(failureRate)
        if (isSlow) changes.slowCallRateExceeded(slowCallRate)
        return isFailing || isSlow
    }
}
