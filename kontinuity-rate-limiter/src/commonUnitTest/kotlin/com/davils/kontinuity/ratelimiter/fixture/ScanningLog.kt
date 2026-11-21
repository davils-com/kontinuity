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
import com.davils.kontinuity.ratelimiter.internal.Reservation

internal class ScanningLog(private val windowNanos: Long, private val maxWaitingCallers: Int) : PermitWindow {
    private val entries = ArrayDeque<Entry>()

    override fun reserve(permits: Int, nowNanos: Long, maxWaitNanos: Long, limit: Int): Reservation {
        expire(nowNanos)
        val earliest = earliestNanos(permits, nowNanos, limit)
        val at = maxOf(a = earliest, b = entries.lastOrNull()?.atNanos ?: earliest)
        val wait = at - nowNanos
        val isFull = wait > 0L && entries.size >= limit.toLong() + maxWaitingCallers
        if (wait > maxWaitNanos || isFull) return Reservation.refused(permits, wait)
        val entry = Entry(at, permits)
        entries.addLast(entry)
        return Reservation.granted(permits, wait) { now -> withdraw(entry, now) }
    }

    override fun available(nowNanos: Long, limit: Int): Int {
        expire(nowNanos)
        val newest = entries.lastOrNull()?.atNanos ?: nowNanos
        if (newest > nowNanos) return 0
        return (limit - used()).coerceAtLeast(0)
    }

    override fun retune(nowNanos: Long, limit: Int) = Unit

    override fun drain(nowNanos: Long, limit: Int): Int {
        val free = available(nowNanos, limit)
        if (free > 0) entries.addLast(Entry(nowNanos, free))
        return free
    }

    private fun used(): Int = entries.sumOf { entry -> entry.permits }

    private fun withdraw(entry: Entry, nowNanos: Long): Boolean {
        if (nowNanos >= entry.atNanos) return false
        return entries.removeAll { standing -> standing === entry }
    }

    private fun expire(nowNanos: Long) {
        while (entries.isNotEmpty() && nowNanos - entries.first().atNanos >= windowNanos) entries.removeFirst()
    }

    private fun earliestNanos(permits: Int, nowNanos: Long, limit: Int): Long {
        var missing = used() + permits - limit
        if (missing <= 0) return nowNanos
        val covering = entries.first { entry ->
            missing -= entry.permits
            missing <= 0
        }
        return covering.atNanos + windowNanos
    }

    private data class Entry(val atNanos: Long, val permits: Int)
}
