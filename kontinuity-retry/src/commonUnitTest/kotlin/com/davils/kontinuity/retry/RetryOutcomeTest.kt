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

package com.davils.kontinuity.retry

import com.davils.kontinuity.core.decorate
import com.davils.kontinuity.retry.fixture.ANSWER
import com.davils.kontinuity.retry.fixture.BROKEN
import com.davils.kontinuity.retry.fixture.WAIT
import com.davils.kontinuity.retry.fixture.broken
import com.davils.kontinuity.retry.fixture.quickRetry
import com.davils.kontinuity.retry.fixture.quietly
import com.davils.kontinuity.retry.fixture.virtualEnvironment
import com.davils.kontinuity.testing.TestRejection
import com.davils.kontinuity.testing.faults
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CancellationException

private const val FIRST_FAILURES: Int = 2
private const val ATTEMPTS: Int = 3
private const val FAILING: Int = -1
private const val CALLS: Long = 7L

@OptIn(ExperimentalKotest::class)
class RetryOutcomeTest : FunSpec({
    coroutineTestScope = true

    test("a call that succeeds at once runs once") {
        val retry = quickRetry(virtualEnvironment())
        retry.execute { ANSWER } shouldBe ANSWER
        val snapshot = retry.snapshot()
        snapshot.successfulWithoutRetry shouldBe 1L
        snapshot.retries shouldBe 0L
    }

    test("a failing call is repeated until it succeeds") {
        val retry = quickRetry(virtualEnvironment())
        val faults = faults { failFirst(FIRST_FAILURES) }
        val start = testCoroutineScheduler.timeSource.markNow()
        retry.execute { faults { ANSWER } } shouldBe ANSWER
        start.elapsedNow() shouldBe WAIT * FIRST_FAILURES
        faults.calls shouldBe ATTEMPTS
        retry.snapshot().successfulWithRetry shouldBe 1L
        retry.snapshot().retries shouldBe FIRST_FAILURES.toLong()
    }

    test("the failure of the last attempt is rethrown as it is when the attempts run out") {
        val retry = quickRetry(virtualEnvironment())
        val failures = mutableListOf<Throwable>()
        val thrown = shouldThrow<IllegalStateException> {
            retry.execute { throw IllegalStateException(BROKEN).also(failures::add) }
        }
        thrown shouldBeSameInstanceAs failures.last()
        failures.size shouldBe ATTEMPTS
        retry.snapshot().failedWithRetry shouldBe 1L
    }

    test("a single attempt never retries") {
        val retry = quickRetry(virtualEnvironment()) { maxAttempts = 1 }
        val faults = faults { failFirst(1) }
        quietly { retry.execute { faults { ANSWER } } }
        faults.calls shouldBe 1
        retry.snapshot().failedWithoutRetry shouldBe 1L
    }

    test("a failure the filter passes or ignores ends the call at once") {
        val retry = quickRetry(virtualEnvironment()) {
            failures {
                record<IllegalStateException>()
                ignore<UnsupportedOperationException>()
            }
        }
        val faults = faults { fail { number -> IllegalArgumentException("call $number") } }
        shouldThrow<IllegalArgumentException> { retry.execute { faults { ANSWER } } }
        shouldThrow<UnsupportedOperationException> { retry.execute { throw UnsupportedOperationException(BROKEN) } }
        faults.calls shouldBe 1
        retry.snapshot().failedWithoutRetry shouldBe 2L
    }

    test("a rejection of another component is never repeated") {
        val retry = quickRetry(virtualEnvironment())
        var attempts = 0
        shouldThrow<TestRejection> {
            retry.execute {
                attempts++
                throw TestRejection()
            }
        }
        attempts shouldBe 1
    }

    test("a cancellation that does not come from the caller is a failure to repeat") {
        val retry = quickRetry(virtualEnvironment())
        val faults = faults { failFirst(1) { CancellationException("timeout of the callee") } }
        retry.execute { faults { ANSWER } } shouldBe ANSWER
        retry.snapshot().successfulWithRetry shouldBe 1L
    }

    test("a failing result is repeated and handed to the discard hook") {
        val discarded = mutableListOf<Any?>()
        val retry = quickRetry(virtualEnvironment()) {
            results { failIf<Int> { value -> value < 0 } }
            onDiscardedResult = discarded::add
        }
        val values = ArrayDeque(listOf(FAILING, FAILING, ANSWER))
        retry.execute { values.removeFirst() } shouldBe ANSWER
        discarded shouldBe listOf(FAILING, FAILING)
        retry.snapshot().successfulWithRetry shouldBe 1L
    }

    test("the last failing result is returned when the attempts run out") {
        val discarded = mutableListOf<Any?>()
        val retry = quickRetry(virtualEnvironment()) {
            results { failIf<Int> { value -> value < 0 } }
            onDiscardedResult = discarded::add
        }
        retry.execute { FAILING } shouldBe FAILING
        discarded.size shouldBe ATTEMPTS - 1
        retry.snapshot().failedWithRetry shouldBe 1L
    }

    test("an exhausted retry fails when asked to and discards the last result") {
        val discarded = mutableListOf<Any?>()
        val retry = quickRetry(virtualEnvironment()) {
            results {
                failIf<Int> { value -> value < 0 }
                failIfNull()
            }
            isFailingAfterMaxAttempts = true
            onDiscardedResult = discarded::add
        }
        val exhausted = shouldThrow<RetriesExhaustedException> { retry.execute { FAILING } }
        exhausted.attempts shouldBe ATTEMPTS
        exhausted.resultType shouldBe "Int"
        exhausted.instance.value shouldBe "payments"
        exhausted.message shouldBe "The retry payments gave up after 3 attempts, because the result filter failed " +
            "every result (last result: Int)"
        discarded.size shouldBe ATTEMPTS
        shouldThrow<RetriesExhaustedException> { retry.execute<Any?> { null } }.resultType shouldBe "null"
        retry.snapshot().failedWithRetry shouldBe 2L
    }

    test("a result filter that throws ends the call with what it threw") {
        val discarded = mutableListOf<Any?>()
        val retry = quickRetry(virtualEnvironment()) {
            results { failIf<Int> { _ -> throw UnsupportedOperationException(BROKEN) } }
            onDiscardedResult = discarded::add
        }
        shouldThrow<UnsupportedOperationException> { retry.execute { ANSWER } }
        discarded shouldBe listOf(ANSWER)
        retry.snapshot().failedWithoutRetry shouldBe 1L
    }

    test("a discard hook that throws is contained") {
        val retry = quickRetry(virtualEnvironment()) {
            results { failIf<Int> { value -> value < 0 } }
            onDiscardedResult = { _ -> broken() }
        }
        val values = ArrayDeque(listOf(FAILING, ANSWER))
        retry.execute { values.removeFirst() } shouldBe ANSWER
    }

    test("every call is counted exactly once") {
        val retry = quickRetry(virtualEnvironment()) {
            results { failIf<Int> { value -> value < 0 } }
            failures { ignore<IllegalArgumentException>() }
        }
        retry.execute { ANSWER }
        val faults = faults { failFirst(1) }
        retry.execute { faults { ANSWER } }
        quietly { retry.execute { broken() } }
        quietly { retry.execute { throw IllegalArgumentException(BROKEN) } }
        retry.execute { FAILING }
        quietly { retry.execute { throw TestRejection() } }
        retry.decorate { ANSWER }()
        val snapshot = retry.snapshot()
        snapshot.calls shouldBe CALLS
        snapshot.successfulWithoutRetry shouldBe 2L
        snapshot.successfulWithRetry shouldBe 1L
        snapshot.failedWithoutRetry shouldBe 2L
        snapshot.failedWithRetry shouldBe 2L
        snapshot.cancelled shouldBe 0L
        snapshot.toString() shouldBe "RetrySnapshot(successfulWithoutRetry=2, successfulWithRetry=1, " +
            "failedWithoutRetry=2, failedWithRetry=2, cancelled=0, retries=5)"
    }
})
