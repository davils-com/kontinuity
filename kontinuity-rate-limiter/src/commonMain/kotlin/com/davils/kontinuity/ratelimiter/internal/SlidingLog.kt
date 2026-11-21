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

import com.davils.kontinuity.core.telemetry.Guard
import com.davils.kontinuity.core.telemetry.guard
import com.davils.kore.core.annotation.DavilsInternal

internal class SlidingLog(private val windowNanos: Long, private val maxWaitingCallers: Int) : PermitWindow {
    private val guard: Guard = guard()
    private val ledger: BookingLedger = BookingLedger()

    val bookings: Int get() = guard.locked { ledger.size }

    val capacity: Int get() = guard.locked { ledger.capacity }

    override fun reserve(permits: Int, nowNanos: Long, maxWaitNanos: Long, limit: Int): Reservation = guard.locked {
        expire(nowNanos)
        book(permits = permits, nowNanos = nowNanos, maxWaitNanos = maxWaitNanos, limit = limit)
    }

    override fun available(nowNanos: Long, limit: Int): Int = guard.locked {
        expire(nowNanos)
        free(nowNanos, limit)
    }

    override fun retune(nowNanos: Long, limit: Int) = Unit

    override fun drain(nowNanos: Long, limit: Int): Int = guard.locked {
        expire(nowNanos)
        val free = free(nowNanos, limit)
        if (free > 0) ledger.append(nowNanos, free)
        free
    }

    private fun book(permits: Int, nowNanos: Long, maxWaitNanos: Long, limit: Int): Reservation {
        val earliest = earliestNanos(permits, nowNanos, limit)
        val newest = ledger.last()?.atNanos ?: earliest
        val at = maxOf(a = earliest, b = newest)
        val wait = at - nowNanos
        val isFull = wait > 0L && ledger.size >= limit.toLong() + maxWaitingCallers
        if (wait > maxWaitNanos || isFull) return Reservation.refused(permits, wait)
        val booking = ledger.append(at, permits)
        return Reservation.granted(permits, wait) { now -> guard.locked { cancel(booking, now) } }
    }

    private fun cancel(booking: Booking, nowNanos: Long): Boolean {
        if (nowNanos >= booking.atNanos || booking.isRemoved) return false
        ledger.cancel(booking)
        return true
    }

    private fun free(nowNanos: Long, limit: Int): Int {
        val newest = ledger.last()?.atNanos ?: nowNanos
        if (newest > nowNanos) return 0
        return (limit - ledger.booked).coerceAtLeast(0L).toInt()
    }

    private fun earliestNanos(permits: Int, nowNanos: Long, limit: Int): Long {
        val missing = ledger.booked + permits - limit
        if (missing <= 0L) return nowNanos
        return ledger.firstCoveringNanos(missing) + windowNanos
    }

    private fun expire(nowNanos: Long) {
        var oldest = ledger.first()
        while (oldest != null && nowNanos - oldest.atNanos >= windowNanos) {
            ledger.cancel(oldest)
            oldest = ledger.first()
        }
    }
}
