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
import kotlin.math.ceil

internal class TokenBucket(private val periodNanos: Long, private val burst: Int, initialLimit: Int) :
    PermitWindow {
    private val state = AtomicReference(
        Bucket(tat = FineTime.of(0L), pace = Pace(periodNanos, initialLimit)),
    )

    override fun reserve(permits: Int, nowNanos: Long, maxWaitNanos: Long, limit: Int): Reservation {
        while (true) {
            val current = state.load()
            val bucket = settled(current, nowNanos, limit)
            val pace = bucket.pace
            val allowance = pace.span((burst - permits).toLong())
            val due = pace.minus(time = bucket.tat, span = allowance).ceilingNanos
            val wait = (due - nowNanos).coerceAtLeast(0L)
            if (wait > maxWaitNanos) return Reservation.refused(permits, wait)
            val dueNanos = nowNanos + wait
            val start = pace.later(time = bucket.tat, nanos = dueNanos)
            val next = Bucket(tat = pace.plus(time = start, span = pace.span(permits.toLong())), pace = pace)
            if (state.compareAndSet(expectedValue = current, newValue = next)) {
                return Reservation.granted(permits, wait) { now ->
                    now < dueNanos && state.compareAndSet(expectedValue = next, newValue = current)
                }
            }
        }
    }

    override fun available(nowNanos: Long, limit: Int): Int {
        val bucket = settled(state.load(), nowNanos, limit)
        return freeOf(bucket, nowNanos)
    }

    override fun drain(nowNanos: Long, limit: Int): Int {
        val previous = state.swap { current -> emptied(current, nowNanos, limit) }
        return freeOf(settled(previous, nowNanos, limit), nowNanos)
    }

    override fun retune(nowNanos: Long, limit: Int) {
        state.swap { current -> settled(current, nowNanos, limit) }
    }

    private fun emptied(current: Bucket, nowNanos: Long, limit: Int): Bucket {
        val bucket = settled(current, nowNanos, limit)
        val pace = bucket.pace
        val empty = pace.plus(time = FineTime.of(nowNanos), span = pace.span(burst.toLong()))
        return Bucket(tat = pace.later(time = bucket.tat, nanos = empty.ceilingNanos), pace = pace)
    }

    private fun freeOf(bucket: Bucket, nowNanos: Long): Int {
        var low = 0
        var high = burst
        while (low < high) {
            val middle = (low + high + 1) / 2
            val isFit = fits(bucket, middle, nowNanos)
            if (isFit) low = middle
            if (!isFit) high = middle - 1
        }
        return low
    }

    private fun fits(bucket: Bucket, permits: Int, nowNanos: Long): Boolean {
        val pace = bucket.pace
        val allowance = pace.span((burst - permits).toLong())
        val due = pace.minus(time = bucket.tat, span = allowance)
        return due.ceilingNanos <= nowNanos
    }

    private fun settled(current: Bucket, nowNanos: Long, limit: Int): Bucket {
        if (current.pace.limit != limit) return retuned(current, nowNanos, Pace(periodNanos, limit))
        return refilled(current, nowNanos)
    }

    private fun refilled(current: Bucket, nowNanos: Long): Bucket {
        if (!current.tat.isBefore(nowNanos)) return current
        return Bucket(tat = FineTime.of(nowNanos), pace = current.pace)
    }

    private fun retuned(current: Bucket, nowNanos: Long, pace: Pace): Bucket {
        val debt = (current.tat.ceilingNanos - nowNanos).coerceAtLeast(0L)
        val owed = ceil(debt.toDouble() * current.pace.limit / periodNanos).toLong()
        val tat = pace.plus(time = FineTime.of(nowNanos), span = pace.span(owed))
        return Bucket(tat = tat, pace = pace)
    }

    private data class Bucket(val tat: FineTime, val pace: Pace)
}
