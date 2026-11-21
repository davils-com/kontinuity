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

package com.davils.kontinuity.hedge.internal

import kotlin.math.ceil

internal class LatencyWindow(private val capacity: Int) {
    private val ring: LongArray = LongArray(capacity)
    private val sorted: LongArray = LongArray(capacity)
    private var next: Int = 0

    var size: Int = 0
        private set

    fun add(nanos: Long) {
        if (size == capacity) remove(ring[next])
        ring[next] = nanos
        next = (next + 1) % capacity
        insert(nanos)
    }

    fun percentile(fraction: Double): Long {
        val rank = ceil(fraction * size).toInt()
        return sorted[rank.coerceIn(minimumValue = 1, maximumValue = size) - 1]
    }

    private fun remove(nanos: Long) {
        val index = lowerBound(nanos)
        sorted.copyInto(destination = sorted, destinationOffset = index, startIndex = index + 1, endIndex = size)
        size -= 1
    }

    private fun insert(nanos: Long) {
        val index = lowerBound(nanos)
        sorted.copyInto(destination = sorted, destinationOffset = index + 1, startIndex = index, endIndex = size)
        sorted[index] = nanos
        size += 1
    }

    private fun lowerBound(nanos: Long): Int {
        var low = 0
        var high = size
        while (low < high) {
            val sum = low + high
            val middle = sum ushr 1
            val isBelow = sorted[middle] < nanos
            if (isBelow) low = middle + 1
            if (!isBelow) high = middle
        }
        return low
    }
}
