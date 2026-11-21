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

package com.davils.kontinuity.timelimiter

import com.davils.kontinuity.core.CallTimeoutException
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.causeChain
import com.davils.kontinuity.testing.testEnvironment
import com.davils.kontinuity.timelimiter.fixture.LIMITER
import com.davils.kontinuity.timelimiter.fixture.LONG
import com.davils.kontinuity.timelimiter.fixture.RESULT
import com.davils.kontinuity.timelimiter.fixture.SHORT
import com.davils.kontinuity.timelimiter.fixture.TIMEOUT
import com.davils.kontinuity.timelimiter.fixture.hardLimiter
import com.davils.kontinuity.timelimiter.fixture.quick
import com.davils.kontinuity.timelimiter.fixture.quietly
import com.davils.kontinuity.timelimiter.fixture.slow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private const val FALLBACK: String = "cached"
private const val BROKEN: String = "broken call"

@OptIn(ExperimentalKotest::class)
class TimeLimiterTest : FunSpec({
    coroutineTestScope = true

    test("a call that ends in time returns its result") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        limiter.quick() shouldBe RESULT
        limiter.kind shouldBe ResilienceKind.TimeLimiter
        limiter.snapshot().successful shouldBe 1L
    }

    test("a hard timeout cancels the call and throws once the timeout is over") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        val started = testCoroutineScheduler.timeSource.markNow()
        var isFinished = false
        val timeout = shouldThrow<CallTimeoutException> {
            limiter.execute {
                delay(LONG)
                isFinished = true
            }
        }
        started.elapsedNow() shouldBe TIMEOUT
        isFinished shouldBe false
        timeout.instance.value shouldBe LIMITER
        timeout.timeout shouldBe TIMEOUT
        limiter.snapshot().timedOut shouldBe 1L
    }

    test("a call that ignores its cancellation and returns late still times out") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        val started = testCoroutineScheduler.timeSource.markNow()
        shouldThrow<CallTimeoutException> {
            limiter.execute {
                withContext(NonCancellable) { delay(LONG) }
                RESULT
            }
        }
        started.elapsedNow() shouldBe LONG
        limiter.snapshot().timedOut shouldBe 1L
    }

    test("a call that fails after its timeout still times out") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        shouldThrow<CallTimeoutException> {
            limiter.execute {
                withContext(NonCancellable) { delay(LONG) }
                error(BROKEN)
            }
        }
        limiter.snapshot().timedOut shouldBe 1L
    }

    test("a call that fails in time is a failure") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        val failure = shouldThrow<IllegalArgumentException> {
            limiter.execute {
                throw IllegalArgumentException(
                    BROKEN,
                )
            }
        }
        failure.message shouldBe BROKEN
        limiter.snapshot().failed shouldBe 1L
    }

    test("the timeout of a callee is a failure, never the cancellation of the caller") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        val failure = shouldThrow<IllegalStateException> { limiter.execute { withTimeout(SHORT) { delay(LONG) } } }
        failure.causeChain().any { cause -> cause is TimeoutCancellationException } shouldBe true
        val snapshot = limiter.snapshot()
        snapshot.failed shouldBe 1L
        snapshot.cancelled shouldBe 0L
        snapshot.timedOut shouldBe 0L
    }

    test("the cancellation of the caller passes through and counts as cancelled") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        val caller = launch { limiter.slow() }
        delay(SHORT)
        caller.cancel()
        caller.join()
        caller.isCancelled shouldBe true
        val snapshot = limiter.snapshot()
        snapshot.cancelled shouldBe 1L
        snapshot.failed shouldBe 0L
        snapshot.timedOut shouldBe 0L
    }

    test("a call that fails after the cancellation of its caller counts as cancelled") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource)) { timeout = Duration.INFINITE }
        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            quietly {
                limiter.execute {
                    withContext(NonCancellable) { delay(LONG) }
                    error(BROKEN)
                }
            }
        }
        caller.cancel()
        caller.join()
        limiter.snapshot().cancelled shouldBe 1L
    }

    test("a timeout handler replaces the timeout with its value") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        var seen: CallTimeoutException? = null
        val value = limiter.execute(onTimeout = { timeout ->
            seen = timeout
            FALLBACK
        }) {
            delay(LONG)
            RESULT
        }
        value shouldBe FALLBACK
        seen?.timeout shouldBe TIMEOUT
        limiter.execute(onTimeout = { FALLBACK }) { RESULT } shouldBe RESULT
        limiter.snapshot().timedOut shouldBe 1L
        limiter.snapshot().successful shouldBe 1L
    }

    test("a failing timeout handler throws its failure with the timeout suppressed") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        val failure = shouldThrow<IllegalStateException> {
            limiter.execute(onTimeout = { error(BROKEN) }) { delay(LONG) }
        }
        failure.suppressedExceptions.single().shouldBeInstanceOf<CallTimeoutException>()
        val rethrown = shouldThrow<CallTimeoutException> {
            limiter.execute(onTimeout = { timeout -> throw timeout }) { delay(LONG) }
        }
        rethrown.suppressedExceptions shouldBe emptyList()
    }

    test("a timeout handler handles nothing but the timeout") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        shouldThrow<IllegalArgumentException> {
            limiter.execute(onTimeout = { FALLBACK }) { throw IllegalArgumentException(BROKEN) }
        }
        limiter.snapshot().failed shouldBe 1L
    }

    test("an infinite timeout never ends a call") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource)) { timeout = Duration.INFINITE }
        limiter.execute {
            delay(1.days)
            RESULT
        } shouldBe RESULT
        limiter.snapshot().successful shouldBe 1L
        limiter.snapshot().detachedInFlight shouldBe 0
    }

    test("a result of null is a result") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        limiter.execute<String?> { null } shouldBe null
        limiter.snapshot().successful shouldBe 1L
    }
})
