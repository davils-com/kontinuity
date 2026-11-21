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

import com.davils.kontinuity.core.backoff.Backoff
import com.davils.kontinuity.core.backoff.Jitter
import com.davils.kontinuity.core.backoff.exponential
import com.davils.kontinuity.core.backoff.fixed
import com.davils.kontinuity.core.backoff.jittered
import com.davils.kontinuity.retry.fixture.ANSWER
import com.davils.kontinuity.retry.fixture.BROKEN
import com.davils.kontinuity.retry.fixture.WAIT
import com.davils.kontinuity.retry.fixture.broken
import com.davils.kontinuity.retry.fixture.quickRetry
import com.davils.kontinuity.retry.fixture.quietly
import com.davils.kontinuity.retry.fixture.virtualEnvironment
import com.davils.kontinuity.retry.fixture.withScope
import com.davils.kontinuity.testing.faults
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val ATTEMPTS: Int = 3
private const val FAILING: Int = -1
private const val SEED: Long = 7L
private const val OTHER_SEED: Long = 8L
private const val JITTERED_ATTEMPTS: Int = 6
private val LONG_WAIT = 5.seconds
private val SHORT_WAIT = 2.seconds

@OptIn(ExperimentalKotest::class)
class RetryWaitTest : FunSpec({
    coroutineTestScope = true

    test("an exponential backoff waits its initial delay before the first retry and grows") {
        val retry = quickRetry(virtualEnvironment()) { backoff = exponential(initial = 1.seconds) }
        elapsedOf(testCoroutineScheduler.timeSource, retry) { broken() } shouldBe 3.seconds
    }

    test("a backoff of zero does not wait") {
        val retry = quickRetry(virtualEnvironment()) { backoff = fixed(Duration.ZERO) }
        elapsedOf(testCoroutineScheduler.timeSource, retry) { broken() } shouldBe Duration.ZERO
        retry.snapshot().retries shouldBe (ATTEMPTS - 1).toLong()
    }

    test("the interval chooses the wait by the failure and by the result") {
        val retry = quickRetry(virtualEnvironment()) {
            results { failIf<Int> { value -> value < 0 } }
            interval { _, failure, result ->
                LONG_WAIT.takeIf { failure is UnsupportedOperationException } ?: SHORT_WAIT.takeIf { result == FAILING }
            }
        }
        elapsedOf(
            testCoroutineScheduler.timeSource,
            retry,
        ) { throw UnsupportedOperationException(BROKEN) } shouldBe LONG_WAIT * (ATTEMPTS - 1)
        elapsedOf(testCoroutineScheduler.timeSource, retry) { FAILING } shouldBe SHORT_WAIT * (ATTEMPTS - 1)
        elapsedOf(testCoroutineScheduler.timeSource, retry) { broken() } shouldBe WAIT * (ATTEMPTS - 1)
    }

    test("the interval learns the attempt that follows") {
        val attempts = mutableListOf<Int>()
        val retry = quickRetry(virtualEnvironment()) {
            interval { attempt, _, _ ->
                attempts += attempt
                null
            }
        }
        elapsedOf(testCoroutineScheduler.timeSource, retry) { broken() }
        attempts shouldBe listOf(2, ATTEMPTS)
    }

    test("an interval that throws falls back to the backoff and its waits are clamped") {
        val throwing = quickRetry(virtualEnvironment()) { interval { _, _, _ -> broken() } }
        elapsedOf(testCoroutineScheduler.timeSource, throwing) { broken() } shouldBe WAIT * (ATTEMPTS - 1)
        val negative = quickRetry(virtualEnvironment()) { interval { _, _, _ -> -WAIT } }
        elapsedOf(testCoroutineScheduler.timeSource, negative) { broken() } shouldBe Duration.ZERO
        val endless = quickRetry(virtualEnvironment()) {
            maxAttempts = 2
            interval { _, _, _ -> Duration.INFINITE }
        }
        elapsedOf(testCoroutineScheduler.timeSource, endless) { broken() } shouldBe Backoff.HARD_MAXIMUM_DELAY
        Backoff.HARD_MAXIMUM_DELAY shouldBe 1.days
    }

    test("the jitter follows the entropy of the environment") {
        val jittered = fixed(LONG_WAIT).jittered(Jitter.Full)
        val seeded = { seed: Long ->
            quickRetry(virtualEnvironment(seed)) {
                backoff = jittered
                maxAttempts = JITTERED_ATTEMPTS
            }
        }
        val first = elapsedOf(testCoroutineScheduler.timeSource, seeded(SEED)) { broken() }
        val again = elapsedOf(testCoroutineScheduler.timeSource, seeded(SEED)) { broken() }
        val other = elapsedOf(testCoroutineScheduler.timeSource, seeded(OTHER_SEED)) { broken() }
        first shouldBe again
        first shouldNotBe other
        first shouldBeLessThanOrEqualTo LONG_WAIT * (JITTERED_ATTEMPTS - 1)
    }

    test("the cancellation of the caller during a wait stops the call and counts as cancelled") {
        val retry = quickRetry(virtualEnvironment())
        val faults = faults { fail() }
        withScope { scope ->
            val job = scope.launch { retry.execute { faults { ANSWER } } }
            delay(WAIT / 2)
            job.cancelAndJoin()
        }
        faults.calls shouldBe 1
        retry.snapshot().cancelled shouldBe 1L
        retry.snapshot().calls shouldBe 1L
    }

    test("the cancellation of the caller during an attempt stops the call and counts as cancelled") {
        val retry = quickRetry(virtualEnvironment())
        withScope { scope ->
            val job = scope.launch {
                retry.execute {
                    delay(LONG_WAIT)
                    broken()
                }
            }
            delay(WAIT)
            job.cancelAndJoin()
        }
        retry.snapshot().cancelled shouldBe 1L
        retry.snapshot().retries shouldBe 0L
    }

    test("a failure thrown after the caller was cancelled is not counted as a failure") {
        val retry = quickRetry(virtualEnvironment())
        withScope { scope ->
            val job = scope.launch {
                retry.execute {
                    currentCoroutineContext().cancel()
                    broken()
                }
            }
            job.join()
        }
        retry.snapshot().cancelled shouldBe 1L
        retry.snapshot().failedWithoutRetry shouldBe 0L
    }
})

private suspend fun elapsedOf(clock: TimeSource.WithComparableMarks, retry: Retry, block: suspend () -> Int): Duration {
    val start = clock.markNow()
    quietly { retry.execute(block) }
    return start.elapsedNow()
}
