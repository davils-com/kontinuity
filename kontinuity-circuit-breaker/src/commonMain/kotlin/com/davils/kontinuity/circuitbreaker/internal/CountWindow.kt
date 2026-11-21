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

internal class CountWindow(private val size: Int) : CallWindow {
    private val outcomes: Array<CallOutcome?> = arrayOfNulls(size)
    private var next: Int = 0
    private var calls: Int = 0
    private var failures: Int = 0
    private var slow: Int = 0
    private var slowFailures: Int = 0

    override fun record(outcome: CallOutcome) {
        outcomes[next]?.let { evicted -> count(evicted, step = -1) }
        outcomes[next] = outcome
        count(outcome, step = 1)
        next = (next + 1) % size
    }

    override fun totals(): WindowTotals = WindowTotals(
        calls = calls,
        failures = failures,
        slow = slow,
        slowFailures = slowFailures,
    )

    private fun count(outcome: CallOutcome, step: Int) {
        calls += step
        if (outcome.isFailure) failures += step
        if (outcome.isSlow) slow += step
        if (outcome.isFailure && outcome.isSlow) slowFailures += step
    }
}
