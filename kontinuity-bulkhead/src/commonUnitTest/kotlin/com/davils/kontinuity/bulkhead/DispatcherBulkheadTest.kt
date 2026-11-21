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

import com.davils.kontinuity.bulkhead.fixture.UnlimitedDispatcher
import com.davils.kontinuity.bulkhead.fixture.decided
import com.davils.kontinuity.bulkhead.fixture.quietly
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val PARALLEL: Int = 2
private const val QUEUE: Int = 1
private const val CALLERS: Int = 12
private const val RESULT: Int = 7
private const val NAME: String = "reports"

@OptIn(ExperimentalKotest::class)
class DispatcherBulkheadTest : FunSpec({
    coroutineTestScope = true

    test("a call runs on the dispatcher and returns its result") {
        val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
        val bulkhead = dispatcherBulkhead(NAME, dispatcher, testEnvironment(testCoroutineScheduler.timeSource))
        bulkhead.execute { RESULT } shouldBe RESULT
        val snapshot = bulkhead.snapshot()
        snapshot.permitted shouldBe 1L
        snapshot.finished shouldBe 1L
        snapshot.remainingCapacity shouldBe
            DispatcherBulkheadConfig.DEFAULT_CONCURRENT_CALLS + DispatcherBulkheadConfig.DEFAULT_QUEUE_CAPACITY
        shouldThrow<IllegalStateException> { bulkhead.execute { error("broken call") } }
        bulkhead.snapshot().finished shouldBe 2L
    }

    test("a call that finds the capacity full is refused at once") {
        val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
        val bulkhead = dispatcherBulkhead(NAME, dispatcher, testEnvironment(testCoroutineScheduler.timeSource)) {
            maxConcurrentCalls = PARALLEL
            queueCapacity = QUEUE
        }
        val callers = List(PARALLEL + QUEUE) { launch { bulkhead.execute { delay(1.seconds) } } }
        testCoroutineScheduler.runCurrent()
        bulkhead.snapshot().remainingCapacity shouldBe 0
        val clock = testCoroutineScheduler.timeSource
        val start = clock.markNow()
        val refusal = shouldThrow<BulkheadFullException> { bulkhead.execute { RESULT } }
        refusal.isQueueFull shouldBe true
        refusal.message shouldBe "The dispatcher bulkhead reports is full and its queue of waiting calls is full"
        start.elapsedNow() shouldBe Duration.ZERO
        callers.joinAll()
        bulkhead.snapshot().rejected shouldBe 1L
        bulkhead.execute { RESULT } shouldBe RESULT
    }

    test("an admitted call waits in the queue until the dispatcher runs it") {
        val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
        val bulkhead = dispatcherBulkhead(NAME, dispatcher, testEnvironment(testCoroutineScheduler.timeSource))
        val caller = launch(start = CoroutineStart.UNDISPATCHED) { bulkhead.execute { delay(1.seconds) } }
        bulkhead.snapshot().queued shouldBe 1
        testCoroutineScheduler.runCurrent()
        bulkhead.snapshot().queued shouldBe 0
        bulkhead.snapshot().running shouldBe 1
        caller.join()
        bulkhead.snapshot().running shouldBe 0
    }

    test("a caller cancelled before its call starts is counted as cancelled") {
        val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
        val bulkhead = dispatcherBulkhead(NAME, dispatcher, testEnvironment(testCoroutineScheduler.timeSource))
        val caller = launch(start = CoroutineStart.UNDISPATCHED) { bulkhead.execute { RESULT } }
        caller.cancel()
        caller.join()
        val snapshot = bulkhead.snapshot()
        snapshot.cancelled shouldBe 1L
        snapshot.permitted shouldBe 0L
        snapshot.queued shouldBe 0
    }

    test("a caller cancelled while its call runs ends it as finished") {
        val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
        val bulkhead = dispatcherBulkhead(NAME, dispatcher, testEnvironment(testCoroutineScheduler.timeSource))
        val caller = launch { bulkhead.execute { delay(1.seconds) } }
        testCoroutineScheduler.runCurrent()
        caller.cancel()
        caller.join()
        bulkhead.snapshot().finished shouldBe 1L
        bulkhead.snapshot().cancelled shouldBe 0L
    }

    test("every call is counted exactly once") {
        val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
        val bulkhead = dispatcherBulkhead(NAME, dispatcher, testEnvironment(testCoroutineScheduler.timeSource)) {
            maxConcurrentCalls = PARALLEL
            queueCapacity = QUEUE
        }
        val callers = List(CALLERS) { index ->
            launch {
                delay((index % PARALLEL).seconds)
                quietly { bulkhead.execute { delay(1.seconds) } }
            }
        }
        callers.joinAll()
        val snapshot = bulkhead.snapshot()
        snapshot.decided shouldBe CALLERS.toLong()
        snapshot.permitted shouldBe snapshot.finished
        snapshot.running shouldBe 0
    }

    test("a dispatcher that cannot limit its parallelism is refused") {
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        shouldThrow<IllegalArgumentException> { dispatcherBulkhead(NAME, UnlimitedDispatcher, environment) }
    }
})
