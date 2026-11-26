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

import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.concurrency.Guard
import com.davils.kore.core.concurrency.guard
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

private const val SLOTS: Int = 10
private const val NEVER: Long = Long.MIN_VALUE

internal class BudgetLedger(
    private val ratio: Double,
    minRetriesPerSecond: Double,
    window: Duration,
    timeSource: TimeSource.WithComparableMarks,
) {
    private val guard: Guard = guard()
    private val origin = timeSource.markNow()
    private val slotNanos: Long = (window.inWholeNanoseconds / SLOTS).coerceAtLeast(1L)
    private val reserve: Double = minRetriesPerSecond * window.toDouble(DurationUnit.SECONDS)
    private val epochs = LongArray(SLOTS) { NEVER }
    private val deposits = LongArray(SLOTS)
    private val withdrawals = LongArray(SLOTS)

    fun deposit() {
        guard.locked {
            val slot = slotOf(epochNow())
            deposits[slot] += 1L
        }
    }

    fun tryWithdraw(): Boolean = guard.locked {
        val epoch = epochNow()
        val isGranted = balanceAt(epoch) >= 1.0
        if (isGranted) withdrawals[slotOf(epoch)] += 1L
        isGranted
    }

    fun balance(): Long = guard.locked {
        val available = floor(balanceAt(epochNow()))
        available.toLong().coerceAtLeast(0L)
    }

    private fun epochNow(): Long {
        val elapsed = origin.elapsedNow()
        return elapsed.inWholeNanoseconds / slotNanos
    }

    private fun slotOf(epoch: Long): Int {
        val slot = (epoch % SLOTS).toInt()
        if (epochs[slot] == epoch) return slot
        epochs[slot] = epoch
        deposits[slot] = 0L
        withdrawals[slot] = 0L
        return slot
    }

    private fun balanceAt(epoch: Long): Double {
        val deposited = sumWithin(deposits, epoch)
        val withdrawn = sumWithin(withdrawals, epoch)
        return reserve + ratio * deposited - withdrawn
    }

    private fun sumWithin(values: LongArray, epoch: Long): Long {
        var sum = 0L
        repeat(SLOTS) { slot -> if (epochs[slot] > epoch - SLOTS) sum += values[slot] }
        return sum
    }
}
