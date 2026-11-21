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

@file:OptIn(ExperimentalAtomicApi::class)

package com.davils.kontinuity.timelimiter.internal

import com.davils.kontinuity.timelimiter.TimeLimiterSnapshot
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class Totals {
    private val successful = AtomicLong(0L)
    private val failed = AtomicLong(0L)
    private val timedOut = AtomicLong(0L)
    private val cancelled = AtomicLong(0L)
    private val rejected = AtomicLong(0L)
    private val detachedFailed = AtomicLong(0L)

    fun count(outcome: CallOutcome) {
        val total = when (outcome) {
            CallOutcome.Successful -> successful
            CallOutcome.Failed -> failed
            CallOutcome.TimedOut -> timedOut
            CallOutcome.Cancelled -> cancelled
            CallOutcome.Rejected -> rejected
        }
        total.addAndFetch(1L)
    }

    fun detachedFailed() {
        detachedFailed.addAndFetch(1L)
    }

    fun snapshot(detachedInFlight: Int): TimeLimiterSnapshot = TimeLimiterSnapshot(
        successful = successful.load(),
        failed = failed.load(),
        timedOut = timedOut.load(),
        cancelled = cancelled.load(),
        rejected = rejected.load(),
        detachedInFlight = detachedInFlight,
        detachedFailed = detachedFailed.load(),
    )
}
