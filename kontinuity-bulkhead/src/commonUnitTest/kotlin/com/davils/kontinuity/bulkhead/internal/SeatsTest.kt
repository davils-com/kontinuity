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

package com.davils.kontinuity.bulkhead.internal

import com.davils.kontinuity.bulkhead.BulkheadConfig
import com.davils.kontinuity.bulkhead.bulkheadConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SeatsTest : FunSpec({
    test("a waiter that receives its permit before it attaches wakes itself") {
        val seats = Seats(bulkheadConfig { maxConcurrentCalls = 1 })
        seats.enter(mayWait = false) { } shouldBe Entry.Granted
        val queued = seats.enter(mayWait = true) { }
        queued.shouldBeInstanceOf<Entry.Queued>()
        seats.release().size shouldBe 1
        var woken = 0
        val wake = seats.attach(queued.waiter) { woken += 1 }
        wake()
        woken shouldBe 1
        seats.finish(queued.waiter) shouldBe true
    }

    test("a waiter that attaches before its permit is woken by the release") {
        val seats = Seats(BulkheadConfig.Default)
        val config = bulkheadConfig { maxConcurrentCalls = 1 }
        seats.resize(config).size shouldBe 0
        seats.enter(mayWait = false) { } shouldBe Entry.Granted
        val queued = seats.enter(mayWait = true) { }
        queued.shouldBeInstanceOf<Entry.Queued>()
        var woken = 0
        val nothing = seats.attach(queued.waiter) { woken += 1 }
        nothing()
        woken shouldBe 0
        seats.release().forEach { wake -> wake() }
        woken shouldBe 1
        seats.isGranted(queued.waiter) shouldBe true
    }
})
