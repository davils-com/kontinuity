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

import com.davils.kontinuity.bulkhead.fixture.LONG_WAIT
import com.davils.kontinuity.bulkhead.fixture.waitingBulkhead
import com.davils.kontinuity.core.invoke
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private const val LIMIT: Int = 3
private const val CALLERS: Int = 20
private const val QUEUED: Int = 5
private const val RESULT: Int = 42
private val SHORT = 5.seconds
private val HELD = 2.seconds

@OptIn(ExperimentalKotest::class)
class BulkheadTest : FunSpec({
    coroutineTestScope = true

    test("a call runs while it holds a permit and gives it back") {
        val bulkhead = bulkhead("plain", testEnvironment(testCoroutineScheduler.timeSource))
        bulkhead.execute { RESULT } shouldBe RESULT
        bulkhead { RESULT } shouldBe RESULT
        val snapshot = bulkhead.snapshot()
        snapshot.permitted shouldBe 2L
        snapshot.finished shouldBe 2L
        snapshot.availableConcurrentCalls shouldBe BulkheadConfig.DEFAULT_CONCURRENT_CALLS
        snapshot.concurrentCalls shouldBe 0
    }

    test("a failing call gives its permit back") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        shouldThrow<IllegalStateException> { bulkhead.execute { error("broken call") } }
        bulkhead.snapshot().finished shouldBe 1L
        bulkhead.tryAcquirePermission().shouldNotBeNull()
    }

    test("never more calls run at once than the bulkhead permits") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource), limit = LIMIT)
        var running = 0
        var highest = 0
        val callers = List(CALLERS) {
            launch {
                bulkhead.execute {
                    running += 1
                    highest = maxOf(a = highest, b = running)
                    delay(1.seconds)
                    running -= 1
                }
            }
        }
        callers.joinAll()
        highest shouldBe LIMIT
        bulkhead.snapshot().permitted shouldBe CALLERS.toLong()
        bulkhead.snapshot().finished shouldBe CALLERS.toLong()
    }

    test("waiting calls receive their permits first in, first out") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        val holder = bulkhead.acquirePermission()
        val order = mutableListOf<Int>()
        val callers = List(QUEUED) { index ->
            launch {
                bulkhead.execute {
                    order += index
                    delay(1.seconds)
                }
            }
        }
        testCoroutineScheduler.runCurrent()
        bulkhead.snapshot().waitingCalls shouldBe QUEUED
        holder.release()
        bulkhead.tryAcquirePermission().shouldBeNull()
        callers.joinAll()
        order shouldBe List(QUEUED) { index -> index }
    }

    test("a newcomer never takes a permit while calls wait") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        val holder = bulkhead.acquirePermission()
        val waiter = launch { bulkhead.execute { delay(1.seconds) } }
        testCoroutineScheduler.runCurrent()
        holder.release()
        bulkhead.tryAcquirePermission().shouldBeNull()
        waiter.join()
        bulkhead.snapshot().rejected shouldBe 1L
    }

    test("a full bulkhead without a wait refuses at once") {
        val bulkhead = bulkhead("strict", testEnvironment(testCoroutineScheduler.timeSource)) {
            maxConcurrentCalls = 1
        }
        val holder = bulkhead.acquirePermission()
        val clock = testCoroutineScheduler.timeSource
        val start = clock.markNow()
        val refusal = shouldThrow<BulkheadFullException> { bulkhead.execute { RESULT } }
        refusal.isQueueFull shouldBe false
        start.elapsedNow() shouldBe Duration.ZERO
        holder.release()
        bulkhead.snapshot().rejected shouldBe 1L
    }

    test("a wait that runs out is refused after exactly its timeout") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        val holder = bulkhead.acquirePermission()
        val clock = testCoroutineScheduler.timeSource
        val start = clock.markNow()
        val refusal = shouldThrow<BulkheadFullException> { bulkhead.acquirePermission(SHORT) }
        refusal.isQueueFull shouldBe false
        start.elapsedNow() shouldBe SHORT
        bulkhead.snapshot().waitingCalls shouldBe 0
        holder.release()
        bulkhead.snapshot().availableConcurrentCalls shouldBe 1
    }

    test("a waiting call proceeds once a permit is released in time") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        val holder = bulkhead.acquirePermission()
        launch {
            delay(HELD)
            holder.release()
        }
        val clock = testCoroutineScheduler.timeSource
        val start = clock.markNow()
        val permit = bulkhead.acquirePermission(SHORT)
        start.elapsedNow() shouldBe HELD
        delay(HELD)
        permit.held shouldBe HELD
        permit.release()
        bulkhead.snapshot().finished shouldBe 2L
    }

    test("a full queue refuses at once") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource)) { maxWaitingCalls = 1 }
        val holder = bulkhead.acquirePermission()
        val waiter = launch { bulkhead.execute { } }
        testCoroutineScheduler.runCurrent()
        val clock = testCoroutineScheduler.timeSource
        val start = clock.markNow()
        val refusal = shouldThrow<BulkheadFullException> { bulkhead.acquirePermission() }
        refusal.isQueueFull shouldBe true
        start.elapsedNow() shouldBe Duration.ZERO
        holder.release()
        waiter.join()
    }

    test("a bulkhead without room for waiting calls never lets a call wait") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource)) { maxWaitingCalls = 0 }
        val holder = bulkhead.acquirePermission()
        shouldThrow<BulkheadFullException> { bulkhead.acquirePermission() }.isQueueFull shouldBe true
        holder.release()
    }

    test("a wait is bounded by the hard ceiling") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        bulkhead.acquirePermission(BulkheadConfig.HARD_WAIT).release()
        bulkhead.acquirePermission(Duration.ZERO).release()
        shouldThrow<IllegalArgumentException> { bulkhead.acquirePermission(BulkheadConfig.HARD_WAIT + 1.nanoseconds) }
        shouldThrow<IllegalArgumentException> { bulkhead.acquirePermission((-1).seconds) }
        shouldThrow<IllegalArgumentException> { bulkhead.acquirePermission(Duration.INFINITE) }
        bulkhead.snapshot().permitted shouldBe 2L
    }

    test("a permit is given back once however often it is released") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource), limit = 2)
        val permit = bulkhead.acquirePermission()
        val other = bulkhead.acquirePermission()
        permit.toString() shouldBe "BulkheadPermit(payments, released=false)"
        permit.release()
        permit.release()
        permit.isReleased shouldBe true
        permit.toString() shouldBe "BulkheadPermit(payments, released=true)"
        bulkhead.snapshot().finished shouldBe 1L
        bulkhead.snapshot().concurrentCalls shouldBe 1
        other.release()
        bulkhead.config.maxWaitDuration shouldBe LONG_WAIT
    }
})
