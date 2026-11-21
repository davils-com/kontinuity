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

package com.davils.kontinuity.bulkhead

import com.davils.kontinuity.bulkhead.fixture.waitingBulkhead
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private const val WAITERS: Int = 3
private const val WIDE: Int = 4
private const val NARROW_FROM: Int = 3

@OptIn(ExperimentalKotest::class)
class BulkheadResizeTest : FunSpec({
    coroutineTestScope = true

    test("a higher limit hands permits to waiting calls at once") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        val holder = bulkhead.acquirePermission()
        val permits = mutableListOf<BulkheadPermit>()
        val waiters = List(WAITERS) { launch { permits += bulkhead.acquirePermission() } }
        testCoroutineScheduler.runCurrent()
        bulkhead.snapshot().waitingCalls shouldBe WAITERS
        bulkhead.changeConfig(bulkheadConfig(bulkhead.config) { maxConcurrentCalls = WIDE })
        waiters.joinAll()
        permits.size shouldBe WAITERS
        val snapshot = bulkhead.snapshot()
        snapshot.concurrentCalls shouldBe WIDE
        snapshot.maxAllowedConcurrentCalls shouldBe WIDE
        snapshot.waitingCalls shouldBe 0
        holder.release()
        permits.forEach(BulkheadPermit::release)
        bulkhead.snapshot().availableConcurrentCalls shouldBe WIDE
    }

    test("a lower limit lets running calls finish and admits new ones only below it") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource), limit = NARROW_FROM)
        val running = List(NARROW_FROM) { bulkhead.acquirePermission() }
        bulkhead.changeConfig(bulkheadConfig(bulkhead.config) { maxConcurrentCalls = 1 })
        bulkhead.snapshot().availableConcurrentCalls shouldBe 0
        bulkhead.snapshot().concurrentCalls shouldBe NARROW_FROM
        running[0].release()
        bulkhead.tryAcquirePermission().shouldBeNull()
        running[1].release()
        bulkhead.tryAcquirePermission().shouldBeNull()
        running[2].release()
        bulkhead.tryAcquirePermission().shouldNotBeNull()
        bulkhead.toString() shouldBe "Bulkhead(payments, maxConcurrentCalls=1)"
    }

    test("a smaller queue keeps its waiting calls and refuses newcomers") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        val holder = bulkhead.acquirePermission()
        val waiters = List(WAITERS) { launch { bulkhead.acquirePermission().release() } }
        testCoroutineScheduler.runCurrent()
        bulkhead.changeConfig(bulkheadConfig(bulkhead.config) { maxWaitingCalls = 1 })
        bulkhead.snapshot().waitingCalls shouldBe WAITERS
        shouldThrow<BulkheadFullException> { bulkhead.acquirePermission() }.isQueueFull shouldBe true
        holder.release()
        waiters.joinAll()
        bulkhead.snapshot().finished shouldBe (WAITERS + 1).toLong()
    }
})
