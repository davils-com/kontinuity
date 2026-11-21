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

package com.davils.kontinuity.hedge.internal

import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kontinuity.hedge.HedgeEvent
import com.davils.kore.core.annotation.DavilsInternal
import kotlin.time.Duration

private const val UNNAMED: String = "Throwable"

internal class Reporter(
    private val name: InstanceName,
    private val ledger: Ledger,
    private val sink: EventSink<HedgeEvent>,
    private val meters: HedgeMeters?,
) {
    init {
        meters?.delay(ledger.delay)
    }

    fun hedgeStarted(attempt: Int, after: Duration) {
        ledger.hedgeStarted()
        meters?.hedge()
        sink.emit { HedgeEvent.HedgeStarted(instance = name, attempt = attempt, after = after) }
    }

    fun won(attempt: Int, duration: Duration) {
        if (attempt == 0) primaryWon(duration)
        if (attempt > 0) hedgeWon(attempt, duration)
    }

    fun failed(attempts: Int, failureType: String, duration: Duration?) {
        ledger.failed()
        meters?.call(HedgeMeters.FAILED, duration)
        sink.emit { HedgeEvent.Failed(instance = name, attempts = attempts, failureType = failureType) }
    }

    fun failed(attempts: Int, failure: Throwable, duration: Duration) {
        failed(attempts = attempts, failureType = failure::class.simpleName ?: UNNAMED, duration = duration)
    }

    fun cancelled() {
        ledger.cancelled()
        meters?.call(HedgeMeters.CANCELLED, duration = null)
        sink.emit { HedgeEvent.Cancelled(name) }
    }

    private fun primaryWon(duration: Duration) {
        val delay = ledger.primarySucceeded(duration)
        meters?.call(HedgeMeters.PRIMARY_SUCCEEDED, duration)
        meters?.delay(delay)
        sink.emit { HedgeEvent.PrimarySucceeded(name, duration) }
    }

    private fun hedgeWon(attempt: Int, duration: Duration) {
        ledger.hedgeSucceeded()
        meters?.call(HedgeMeters.HEDGE_SUCCEEDED, duration)
        sink.emit { HedgeEvent.HedgeSucceeded(instance = name, attempt = attempt, duration = duration) }
    }
}
