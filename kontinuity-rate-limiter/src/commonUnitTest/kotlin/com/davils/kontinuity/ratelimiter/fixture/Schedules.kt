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

package com.davils.kontinuity.ratelimiter.fixture

import com.davils.kontinuity.ratelimiter.internal.PermitWindow
import io.kotest.matchers.longs.shouldBeLessThanOrEqual

internal const val MAX_WAIT: Long = 10_000L
internal const val ACTIONS: Int = 10
internal const val CANCEL: Int = 7

internal fun run(window: PermitWindow, limit: Int, gaps: List<Long>, picks: List<Int>): List<Granted> {
    val granted = mutableListOf<Granted>()
    var now = 0L
    gaps.zip(picks).forEach { (gap, pick) ->
        now += gap
        val permits = pick % limit + 1
        val reservation = window.reserve(permits = permits, nowNanos = now, maxWaitNanos = MAX_WAIT, limit = limit)
        if (reservation.isGranted) granted += Granted(now + reservation.waitNanos, permits, reservation)
        val queued = granted.filter { entry -> entry.atNanos > now }
        val withdrawn = queued.getOrNull(pick % ACTIONS).takeIf { pick % ACTIONS == CANCEL }
        if (withdrawn != null && withdrawn.reservation.refund(now)) granted -= withdrawn
    }
    return granted
}

internal fun heaviestSpan(granted: List<Granted>, spanNanos: Long): Long = granted.maxOfOrNull { first ->
    permitsOf(granted.filter { other -> other.atNanos in first.atNanos until first.atNanos + spanNanos })
} ?: 0L

internal fun heaviestCycle(granted: List<Granted>, periodNanos: Long): Long {
    val byCycle = granted.groupBy { entry -> entry.atNanos / periodNanos }
    return byCycle.values.maxOfOrNull(::permitsOf) ?: 0L
}

internal fun checkBucket(granted: List<Granted>, burst: Int, limit: Int, periodNanos: Long) {
    val ordered = granted.sortedBy { entry -> entry.atNanos }
    for (first in ordered.indices) {
        var taken = 0L
        for (last in first until ordered.size) {
            taken += ordered[last].permits
            val refilled = (ordered[last].atNanos - ordered[first].atNanos) * limit / periodNanos
            taken shouldBeLessThanOrEqual burst + refilled
        }
    }
}

private fun permitsOf(granted: List<Granted>): Long = granted.sumOf { entry -> entry.permits.toLong() }
