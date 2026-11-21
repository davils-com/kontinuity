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

package com.davils.kontinuity.circuitbreaker.internal

import kotlin.time.Duration
import kotlin.time.TimeSource

internal class TimeWindow(length: Duration, private val buckets: Int, timeSource: TimeSource.WithComparableMarks) :
    CallWindow {
    private val start = timeSource.markNow()
    private val bucketNanos: Long = (length.inWholeNanoseconds / buckets).coerceAtLeast(1L)
    private val epochs: LongArray = LongArray(buckets) { NO_EPOCH }
    private val calls: IntArray = IntArray(buckets)
    private val failures: IntArray = IntArray(buckets)
    private val slow: IntArray = IntArray(buckets)
    private val slowFailures: IntArray = IntArray(buckets)

    override fun record(outcome: CallOutcome) {
        val epoch = currentEpoch()
        val index = (epoch % buckets).toInt()
        if (epochs[index] != epoch) clear(index, epoch)
        calls[index] += 1
        if (outcome.isFailure) failures[index] += 1
        if (outcome.isSlow) slow[index] += 1
        if (outcome.isFailure && outcome.isSlow) slowFailures[index] += 1
    }

    override fun totals(): WindowTotals {
        val oldest = currentEpoch() - buckets
        val live = (0 until buckets).filter { index -> epochs[index] > oldest }
        return WindowTotals(
            calls = live.sumOf { index -> calls[index] },
            failures = live.sumOf { index -> failures[index] },
            slow = live.sumOf { index -> slow[index] },
            slowFailures = live.sumOf { index -> slowFailures[index] },
        )
    }

    private fun currentEpoch(): Long = start.elapsedNow().inWholeNanoseconds / bucketNanos

    private fun clear(index: Int, epoch: Long) {
        epochs[index] = epoch
        calls[index] = 0
        failures[index] = 0
        slow[index] = 0
        slowFailures[index] = 0
    }

    private companion object {
        const val NO_EPOCH: Long = Long.MIN_VALUE
    }
}
