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

package com.davils.kontinuity.ratelimiter.internal

import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kontinuity.ratelimiter.RateLimiterEvent
import com.davils.kore.core.annotation.DavilsInternal
import kotlin.time.Duration

internal class Reporter(
    private val name: InstanceName,
    private val sink: EventSink<RateLimiterEvent>,
    private val meters: LimiterMeters?,
) {
    fun acquired(permits: Int, waited: Duration) {
        meters?.call(LimiterMeters.ACQUIRED)
        meters?.waited(waited)
        sink.emit { RateLimiterEvent.Acquired(name, permits, waited) }
    }

    fun rejected(permits: Int, availableIn: Duration) {
        meters?.call(LimiterMeters.REJECTED)
        sink.emit { RateLimiterEvent.Rejected(name, permits, availableIn) }
    }

    fun cancelled(permits: Int, isRefunded: Boolean) {
        meters?.call(LimiterMeters.CANCELLED)
        sink.emit { RateLimiterEvent.Cancelled(name, permits, isRefunded) }
    }

    fun drained(permits: Int) {
        sink.emit { RateLimiterEvent.Drained(name, permits) }
    }

    fun changed(limits: Limits) {
        sink.emit { RateLimiterEvent.LimitChanged(name, limits.limitForPeriod, limits.timeout) }
    }

    fun levels(availablePermissions: () -> Int, waitingCallers: Int) {
        val target = meters ?: return
        target.levels(availablePermissions = availablePermissions(), waitingCallers = waitingCallers)
    }
}
