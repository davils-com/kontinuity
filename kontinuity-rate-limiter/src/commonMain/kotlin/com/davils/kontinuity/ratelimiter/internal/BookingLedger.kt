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

package com.davils.kontinuity.ratelimiter.internal

internal class BookingLedger {
    private var slots: Array<Booking?> = arrayOfNulls(MIN_CAPACITY)
    private var sums: LongArray = LongArray(MIN_CAPACITY + 1)
    private var times: LongArray = LongArray(MIN_CAPACITY)
    private var head: Int = 0
    private var tail: Int = 0
    private var holes: Int = 0

    var booked: Long = 0L
        private set

    val size: Int get() = tail - head - holes

    val capacity: Int get() = slots.size

    fun first(): Booking? = slots.getOrNull(head).takeIf { head < tail }

    fun last(): Booking? = slots.getOrNull(tail - 1).takeIf { head < tail }

    fun append(atNanos: Long, permits: Int): Booking {
        if (tail == slots.size) rebuild(capacityFor(size + 1))
        val booking = Booking(atNanos = atNanos, permits = permits, slot = tail)
        slots[tail] = booking
        times[tail] = atNanos
        tail += 1
        addAt(booking.slot, permits.toLong())
        booked += permits
        return booking
    }

    fun cancel(booking: Booking) {
        if (!booking.isRemoved) drop(booking)
    }

    fun firstCoveringNanos(permits: Long): Long {
        var position = 0
        var missing = permits
        var step = slots.size
        while (step > 0) {
            val next = position + step
            if (sums[next] < missing) {
                position = next
                missing -= sums[next]
            }
            step = step shr 1
        }
        return times[position]
    }

    private fun drop(booking: Booking) {
        val slot = booking.slot
        addAt(slot, -booking.permits.toLong())
        booked -= booking.permits
        slots[slot] = null
        booking.remove()
        holes += 1
        closeEnds()
        compactIfSparse()
    }

    private fun closeEnds() {
        while (head < tail && slots[head] == null) {
            head += 1
            holes -= 1
        }
        while (tail > head && slots[tail - 1] == null) {
            tail -= 1
            holes -= 1
        }
        if (head != tail) return
        head = 0
        tail = 0
    }

    private fun compactIfSparse() {
        val wasted = head + holes
        val isSparse = wasted > size && slots.size > MIN_CAPACITY
        if (isSparse) rebuild(capacityFor(size))
    }

    private fun rebuild(newCapacity: Int) {
        val live = arrayOfNulls<Booking>(newCapacity)
        val newSums = LongArray(newCapacity + 1)
        val newTimes = LongArray(newCapacity)
        var next = 0
        for (index in head until tail) {
            val booking = slots[index] ?: continue
            booking.moveTo(next)
            live[next] = booking
            newTimes[next] = booking.atNanos
            newSums[next + 1] += booking.permits.toLong()
            next += 1
        }
        var node = 1
        while (node <= newCapacity) {
            val parent = node + lowestBit(node)
            if (parent <= newCapacity) newSums[parent] += newSums[node]
            node += 1
        }
        slots = live
        sums = newSums
        times = newTimes
        head = 0
        tail = next
        holes = 0
    }

    private fun addAt(slot: Int, delta: Long) {
        var node = slot + 1
        while (node <= slots.size) {
            sums[node] += delta
            node += lowestBit(node)
        }
    }

    private companion object {
        const val MIN_CAPACITY: Int = 16

        fun lowestBit(value: Int): Int = value and -value

        fun capacityFor(bookings: Int): Int {
            var capacity = MIN_CAPACITY
            while (capacity < bookings * 2) capacity = capacity shl 1
            return capacity
        }
    }
}
