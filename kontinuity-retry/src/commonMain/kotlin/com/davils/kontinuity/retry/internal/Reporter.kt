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

package com.davils.kontinuity.retry.internal

import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kontinuity.retry.RetryEvent
import com.davils.kore.core.annotation.DavilsInternal
import kotlin.time.Duration

internal class Reporter(
    val name: InstanceName,
    private val sink: EventSink<RetryEvent>,
    private val meters: RetryMeters?,
    private val counts: Counts,
) {
    fun retrying(attempt: Int, delay: Duration, reasonType: String) {
        counts.retried()
        meters?.retry(delay)
        sink.emit { RetryEvent.Retrying(instance = name, attempt = attempt, delay = delay, reasonType = reasonType) }
    }

    fun budgetDenied(attempt: Int) {
        sink.emit { RetryEvent.BudgetDenied(name, attempt) }
    }

    fun succeeded(attempts: Int) {
        ended(CallKind.successful(attempts))
        sink.emit { RetryEvent.Succeeded(name, attempts) }
    }

    fun exhausted(attempts: Int, failureType: String) {
        ended(CallKind.failed(attempts))
        sink.emit { RetryEvent.Exhausted(instance = name, attempts = attempts, failureType = failureType) }
    }

    fun ignored(attempts: Int, failureType: String) {
        ended(CallKind.failed(attempts))
        sink.emit { RetryEvent.IgnoredFailure(name, failureType) }
    }

    fun abandoned(attempts: Int, isCancelled: Boolean) {
        val kind = CallKind.Cancelled.takeIf { isCancelled } ?: CallKind.failed(attempts)
        ended(kind)
    }

    private fun ended(kind: CallKind) {
        counts.count(kind)
        meters?.call(kind)
    }
}
