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

package com.davils.kontinuity.ratelimiter.internal

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class FixedWindow(private val periodNanos: Long, initialLimit: Int) : PermitWindow {
    private val state = AtomicReference(Cycle(index = 0L, remaining = initialLimit))

    override fun reserve(permits: Int, nowNanos: Long, maxWaitNanos: Long, limit: Int): Reservation {
        while (true) {
            val current = state.load()
            val next = booked(current = current, permits = permits, nowNanos = nowNanos, limit = limit)
            val wait = (next.index * periodNanos - nowNanos).coerceAtLeast(0L)
            if (wait > maxWaitNanos) return Reservation.refused(permits, wait)
            if (state.compareAndSet(expectedValue = current, newValue = next)) {
                val charge = Charge(permits = permits, limit = limit, before = current, after = next)
                return Reservation.granted(permits, wait) { now -> refund(charge, now) }
            }
        }
    }

    override fun available(nowNanos: Long, limit: Int): Int = freeOf(opened(state.load(), nowNanos, limit), nowNanos)

    override fun retune(nowNanos: Long, limit: Int) = Unit

    override fun drain(nowNanos: Long, limit: Int): Int {
        val previous = state.swap { current -> emptied(current, nowNanos, limit) }
        return freeOf(opened(previous, nowNanos, limit), nowNanos)
    }

    private fun refund(charge: Charge, nowNanos: Long): Boolean =
        state.trySwap { current -> undone(current, charge, nowNanos) }

    private fun undone(current: Cycle, charge: Charge, nowNanos: Long): Cycle? {
        val isWaiting = nowNanos / periodNanos < charge.after.index && current.index == charge.after.index
        if (!isWaiting) return null
        val restored = minOf(a = current.remaining + charge.permits, b = charge.limit)
        return charge.before.takeIf { current === charge.after } ?: Cycle(index = current.index, remaining = restored)
    }

    private fun emptied(current: Cycle, nowNanos: Long, limit: Int): Cycle {
        val opened = opened(current, nowNanos, limit)
        return Cycle(index = opened.index, remaining = opened.remaining - freeOf(opened, nowNanos))
    }

    private fun freeOf(opened: Cycle, nowNanos: Long): Int =
        opened.remaining.takeIf { opened.index == nowNanos / periodNanos } ?: 0

    private fun booked(current: Cycle, permits: Int, nowNanos: Long, limit: Int): Cycle {
        val start = opened(current, nowNanos, limit)
        if (start.remaining >= permits) return Cycle(index = start.index, remaining = start.remaining - permits)
        return Cycle(index = start.index + 1L, remaining = limit - permits)
    }

    private fun opened(current: Cycle, nowNanos: Long, limit: Int): Cycle {
        val index = nowNanos / periodNanos
        if (index > current.index) return Cycle(index = index, remaining = limit)
        return current
    }

    private data class Cycle(val index: Long, val remaining: Int)

    private data class Charge(val permits: Int, val limit: Int, val before: Cycle, val after: Cycle)
}
