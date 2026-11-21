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

package com.davils.kontinuity.ratelimiter

import com.davils.kontinuity.ratelimiter.internal.BookingLedger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val MANY: Int = 100
private const val LATER: Long = 30L
private const val LATEST: Long = 40L
private const val PERMITS: Int = 2

class BookingLedgerTest : FunSpec({
    test("a ledger sums its bookings and finds the first that covers a count") {
        val ledger = BookingLedger()
        ledger.first() shouldBe null
        ledger.last() shouldBe null
        val first = ledger.append(atNanos = 0L, permits = 1)
        val middle = ledger.append(atNanos = LATER, permits = PERMITS)
        ledger.append(atNanos = LATEST, permits = 1)
        ledger.booked shouldBe (PERMITS + 2).toLong()
        ledger.firstCoveringNanos(1L) shouldBe 0L
        ledger.firstCoveringNanos(2L) shouldBe LATER
        ledger.firstCoveringNanos((PERMITS + 2).toLong()) shouldBe LATEST
        ledger.cancel(middle)
        ledger.cancel(middle)
        ledger.size shouldBe 2
        ledger.firstCoveringNanos(2L) shouldBe LATEST
        ledger.cancel(first)
        ledger.first()?.atNanos shouldBe LATEST
    }

    test("a ledger grows with its bookings and shrinks once they leave") {
        val ledger = BookingLedger()
        val bookings = List(MANY) { index -> ledger.append(atNanos = index.toLong(), permits = 1) }
        val grown = ledger.capacity
        bookings.dropLast(1).forEach(ledger::cancel)
        ledger.size shouldBe 1
        (ledger.capacity < grown) shouldBe true
        ledger.firstCoveringNanos(1L) shouldBe (MANY - 1).toLong()
        ledger.cancel(bookings.last())
        ledger.size shouldBe 0
        ledger.booked shouldBe 0L
        ledger.append(atNanos = LATEST, permits = 1).slot shouldBe 0
    }

    test("a ledger closes the holes at its end") {
        val ledger = BookingLedger()
        val bookings = List(MANY) { index -> ledger.append(atNanos = index.toLong(), permits = 1) }
        val newestFirst = bookings.drop(1).reversed()
        newestFirst.forEach(ledger::cancel)
        ledger.last()?.atNanos shouldBe 0L
        ledger.size shouldBe 1
    }
})
