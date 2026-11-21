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

package com.davils.kontinuity.timelimiter.internal

import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kontinuity.timelimiter.TimeLimiterEvent
import com.davils.kontinuity.timelimiter.TimeLimiterSnapshot
import com.davils.kore.core.annotation.DavilsInternal
import kotlin.time.Duration

private const val UNNAMED: String = "Throwable"

internal class Reporter(
    private val name: InstanceName,
    private val sink: EventSink<TimeLimiterEvent>,
    private val meters: LimiterMeters?,
) {
    private val totals = Totals()

    fun succeeded(elapsed: Duration) {
        counted(CallOutcome.Successful, elapsed)
        sink.emit { TimeLimiterEvent.Succeeded(name, elapsed) }
    }

    fun failed(elapsed: Duration, failure: Throwable) {
        counted(CallOutcome.Failed, elapsed)
        sink.emit { TimeLimiterEvent.Failed(instance = name, duration = elapsed, failureType = typeOf(failure)) }
    }

    fun timedOut(elapsed: Duration, timeout: Duration) {
        counted(CallOutcome.TimedOut, elapsed)
        sink.emit { TimeLimiterEvent.TimedOut(name, timeout) }
    }

    fun cancelled(elapsed: Duration) {
        counted(CallOutcome.Cancelled, elapsed)
    }

    fun rejected(maxDetached: Int) {
        counted(CallOutcome.Rejected, elapsed = null)
        sink.emit { TimeLimiterEvent.DetachedRejected(name, maxDetached) }
    }

    fun detachedFailed(failure: Throwable) {
        totals.detachedFailed()
        sink.emit { TimeLimiterEvent.DetachedFailed(name, typeOf(failure)) }
    }

    fun detached(inFlight: Int) {
        meters?.detached(inFlight)
    }

    fun snapshot(detachedInFlight: Int): TimeLimiterSnapshot = totals.snapshot(detachedInFlight)

    private fun counted(outcome: CallOutcome, elapsed: Duration?) {
        totals.count(outcome)
        meters?.call(outcome, elapsed)
    }

    private fun typeOf(failure: Throwable): String = failure::class.simpleName ?: UNNAMED
}
