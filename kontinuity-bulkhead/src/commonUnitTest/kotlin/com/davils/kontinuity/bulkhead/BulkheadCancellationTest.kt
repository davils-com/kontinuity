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

import com.davils.kontinuity.bulkhead.fixture.decided
import com.davils.kontinuity.bulkhead.fixture.quietly
import com.davils.kontinuity.bulkhead.fixture.waitingBulkhead
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val LIMIT: Int = 2
private const val WAITING: Int = 4
private const val CALLERS: Int = 30
private const val ARRIVALS: Int = 5
private const val DURATIONS: Int = 4
private const val CANCELLED_EVERY: Int = 7
private val WAIT = 3.seconds
private val CANCEL_AT = 2_500.milliseconds

@OptIn(ExperimentalKotest::class)
class BulkheadCancellationTest : FunSpec({
    coroutineTestScope = true

    test("a cancelled waiter leaves the queue and loses no permit") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        val holder = bulkhead.acquirePermission()
        val waiter = launch { bulkhead.acquirePermission() }
        testCoroutineScheduler.runCurrent()
        bulkhead.snapshot().waitingCalls shouldBe 1
        waiter.cancel()
        testCoroutineScheduler.runCurrent()
        bulkhead.snapshot().waitingCalls shouldBe 0
        bulkhead.snapshot().cancelled shouldBe 1L
        holder.release()
        bulkhead.tryAcquirePermission().shouldNotBeNull()
    }

    test("a permit handed to a waiter cancelled at the same moment goes to the next waiter") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        val holder = bulkhead.acquirePermission()
        var second: BulkheadPermit? = null
        val first = launch { bulkhead.acquirePermission() }
        val next = launch { second = bulkhead.acquirePermission() }
        testCoroutineScheduler.runCurrent()
        holder.release()
        first.cancel()
        next.join()
        second.shouldNotBeNull()
        val snapshot = bulkhead.snapshot()
        snapshot.cancelled shouldBe 1L
        snapshot.permitted shouldBe 2L
        snapshot.concurrentCalls shouldBe 1
        snapshot.waitingCalls shouldBe 0
    }

    test("a waiter cancelled just before a release does not receive the permit") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        val holder = bulkhead.acquirePermission()
        val first = launch { bulkhead.acquirePermission() }
        val next = launch { bulkhead.acquirePermission().release() }
        testCoroutineScheduler.runCurrent()
        first.cancel()
        holder.release()
        next.join()
        bulkhead.snapshot().finished shouldBe 2L
        bulkhead.snapshot().availableConcurrentCalls shouldBe 1
    }

    test("a caller cancelled while it holds a permit gives it back") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        val caller = launch { bulkhead.execute { delay(WAIT) } }
        testCoroutineScheduler.runCurrent()
        caller.cancel()
        caller.join()
        bulkhead.snapshot().finished shouldBe 1L
        bulkhead.snapshot().cancelled shouldBe 0L
        bulkhead.snapshot().availableConcurrentCalls shouldBe 1
    }

    test("a timeout at the moment of a release loses no permit") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        val holder = bulkhead.acquirePermission()
        launch {
            delay(WAIT)
            holder.release()
        }
        quietly { bulkhead.acquirePermission(WAIT).release() }
        testCoroutineScheduler.advanceUntilIdle()
        val snapshot = bulkhead.snapshot()
        snapshot.decided shouldBe 2L
        snapshot.permitted shouldBe snapshot.finished
        snapshot.availableConcurrentCalls shouldBe 1
    }

    test("every request is counted exactly once under waits, timeouts and cancellations") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource), limit = LIMIT) {
            maxWaitDuration = WAIT
            maxWaitingCalls = WAITING
        }
        var asked = 0L
        val callers = List(CALLERS) { index ->
            launch {
                delay((index % ARRIVALS).seconds)
                asked += 1L
                quietly { bulkhead.execute { delay((index % DURATIONS + 1).seconds) } }
            }
        }
        launch {
            delay(CANCEL_AT)
            callers.filterIndexed { index, _ -> index % CANCELLED_EVERY == 0 }.forEach(Job::cancel)
        }
        callers.joinAll()
        val snapshot = bulkhead.snapshot()
        snapshot.decided shouldBe asked
        snapshot.permitted shouldBe snapshot.finished
        snapshot.rejected shouldBeGreaterThan 0L
        snapshot.cancelled shouldBeGreaterThan 0L
        snapshot.availableConcurrentCalls shouldBe LIMIT
        snapshot.waitingCalls shouldBe 0
    }
})
