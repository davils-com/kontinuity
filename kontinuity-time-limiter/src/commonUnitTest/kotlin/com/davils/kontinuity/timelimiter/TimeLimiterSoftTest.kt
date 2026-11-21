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

import com.davils.kontinuity.core.CallRejectedException
import com.davils.kontinuity.core.CallTimeoutException
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.causeChain
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import com.davils.kontinuity.timelimiter.fixture.LIMITER
import com.davils.kontinuity.timelimiter.fixture.LONG
import com.davils.kontinuity.timelimiter.fixture.RESULT
import com.davils.kontinuity.timelimiter.fixture.SHORT
import com.davils.kontinuity.timelimiter.fixture.TIMEOUT
import com.davils.kontinuity.timelimiter.fixture.quick
import com.davils.kontinuity.timelimiter.fixture.quietly
import com.davils.kontinuity.timelimiter.fixture.slow
import com.davils.kontinuity.timelimiter.fixture.softLimiter
import com.davils.kontinuity.timelimiter.fixture.withScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

private const val BROKEN: String = "broken call"
private const val CHECKOUT: String = "checkout"
private const val DETACHED: Int = 2

@OptIn(ExperimentalKotest::class)
class TimeLimiterSoftTest : FunSpec({
    coroutineTestScope = true

    test("a soft call that ends in time returns its result and holds no slot") {
        withScope { scope ->
            val limiter = softLimiter(testEnvironment(testCoroutineScheduler.timeSource, scope))
            limiter.quick() shouldBe RESULT
            val snapshot = limiter.snapshot()
            snapshot.successful shouldBe 1L
            snapshot.detachedInFlight shouldBe 0
        }
    }

    test("a soft timeout cancels the detached call") {
        withScope { scope ->
            val limiter = softLimiter(testEnvironment(testCoroutineScheduler.timeSource, scope))
            var isFinished = false
            var isEnded = false
            shouldThrow<CallTimeoutException> {
                limiter.execute {
                    try {
                        delay(LONG)
                        isFinished = true
                    } finally {
                        isEnded = true
                    }
                }
            }
            testCoroutineScheduler.runCurrent()
            isEnded shouldBe true
            isFinished shouldBe false
            limiter.snapshot().detachedInFlight shouldBe 0
            limiter.snapshot().timedOut shouldBe 1L
        }
    }

    test("a soft timeout reaches the caller in time even if the call ignores its cancellation") {
        withScope { scope ->
            val limiter = softLimiter(testEnvironment(testCoroutineScheduler.timeSource, scope))
            val started = testCoroutineScheduler.timeSource.markNow()
            shouldThrow<CallTimeoutException> {
                limiter.execute {
                    withContext(NonCancellable) { delay(LONG) }
                    RESULT
                }
            }
            started.elapsedNow() shouldBe TIMEOUT
            limiter.snapshot().detachedInFlight shouldBe 1
            delay(LONG)
            limiter.snapshot().detachedInFlight shouldBe 0
        }
    }

    test("a soft timeout without cancelling leaves the call running to its end") {
        withScope { scope ->
            val limiter = softLimiter(
                testEnvironment(testCoroutineScheduler.timeSource, scope),
                isCancellingOnTimeout = false,
            )
            var isFinished = false
            shouldThrow<CallTimeoutException> {
                limiter.execute {
                    delay(LONG)
                    isFinished = true
                }
            }
            limiter.snapshot().detachedInFlight shouldBe 1
            delay(LONG)
            isFinished shouldBe true
            val snapshot = limiter.snapshot()
            snapshot.detachedInFlight shouldBe 0
            snapshot.detachedFailed shouldBe 0L
            snapshot.timedOut shouldBe 1L
        }
    }

    test("a soft limiter runs as many detached calls as it may and refuses the next") {
        withScope { scope ->
            val environment = testEnvironment(testCoroutineScheduler.timeSource, scope)
            val limiter = softLimiter(environment, isCancellingOnTimeout = false, maxDetached = DETACHED)
            repeat(DETACHED) { quietly { limiter.slow() } }
            limiter.snapshot().detachedInFlight shouldBe DETACHED
            val refusal = shouldThrow<DetachedLimitExceededException> { limiter.quick() }
            refusal.shouldBeInstanceOf<CallRejectedException>()
            refusal.maxDetached shouldBe DETACHED
            refusal.kind shouldBe ResilienceKind.TimeLimiter
            refusal.instance.value shouldBe LIMITER
            refusal.message shouldContain "refuses further calls"
            delay(LONG)
            limiter.quick() shouldBe RESULT
            val snapshot = limiter.snapshot()
            snapshot.rejected shouldBe 1L
            snapshot.timedOut shouldBe DETACHED.toLong()
            snapshot.successful shouldBe 1L
        }
    }

    test("a soft limiter with one slot runs one call at a time") {
        withScope { scope ->
            val limiter = softLimiter(testEnvironment(testCoroutineScheduler.timeSource, scope), maxDetached = 1)
            val first = launch { limiter.quick() shouldBe RESULT }
            testCoroutineScheduler.runCurrent()
            shouldThrow<DetachedLimitExceededException> { limiter.quick() }
            first.join()
            limiter.quick() shouldBe RESULT
        }
    }

    test("a detached call that fails after its timeout is reported, never thrown") {
        withScope { scope ->
            val limiter = softLimiter(
                testEnvironment(testCoroutineScheduler.timeSource, scope),
                isCancellingOnTimeout = false,
            )
            val log = recordEvents(limiter.events)
            shouldThrow<CallTimeoutException> {
                limiter.execute {
                    delay(LONG)
                    error(BROKEN)
                }
            }
            delay(LONG)
            log.stop()
            scope.isActive shouldBe true
            limiter.snapshot().detachedFailed shouldBe 1L
            limiter.snapshot().failed shouldBe 0L
            log.only<TimeLimiterEvent.DetachedFailed>().single().failureType shouldBe "IllegalStateException"
        }
    }

    test("a soft call that fails in time throws its failure and is no detached failure") {
        withScope { scope ->
            val limiter = softLimiter(testEnvironment(testCoroutineScheduler.timeSource, scope))
            shouldThrow<IllegalArgumentException> { limiter.execute { throw IllegalArgumentException(BROKEN) } }
            testCoroutineScheduler.runCurrent()
            scope.isActive shouldBe true
            limiter.snapshot().failed shouldBe 1L
            limiter.snapshot().detachedFailed shouldBe 0L
        }
    }

    test("the timeout of a callee in a soft call is a failure") {
        withScope { scope ->
            val limiter = softLimiter(testEnvironment(testCoroutineScheduler.timeSource, scope))
            val failure = shouldThrow<IllegalStateException> { limiter.execute { withTimeout(SHORT) { delay(LONG) } } }
            failure.causeChain().any { cause -> cause is TimeoutCancellationException } shouldBe true
            limiter.snapshot().failed shouldBe 1L
        }
    }

    test("the cancellation of the caller of a soft call counts as cancelled and cancels the call") {
        withScope { scope ->
            val limiter = softLimiter(testEnvironment(testCoroutineScheduler.timeSource, scope))
            var isEnded = false
            val caller = launch {
                limiter.execute {
                    try {
                        delay(LONG)
                    } finally {
                        isEnded = true
                    }
                }
            }
            delay(SHORT)
            caller.cancel()
            caller.join()
            testCoroutineScheduler.runCurrent()
            isEnded shouldBe true
            limiter.snapshot().cancelled shouldBe 1L
            limiter.snapshot().detachedInFlight shouldBe 0
        }
    }

    test("a soft call outlives the cancellation of its caller unless it cancels") {
        withScope { scope ->
            val limiter = softLimiter(
                testEnvironment(testCoroutineScheduler.timeSource, scope),
                isCancellingOnTimeout = false,
            )
            val caller = launch { limiter.slow() }
            delay(SHORT)
            caller.cancel()
            caller.join()
            limiter.snapshot().detachedInFlight shouldBe 1
            delay(LONG)
            limiter.snapshot().detachedInFlight shouldBe 0
            limiter.snapshot().cancelled shouldBe 1L
        }
    }

    test("a soft call fails when the scope of its environment is cancelled") {
        withScope { scope ->
            scope.cancel()
            val limiter = softLimiter(testEnvironment(testCoroutineScheduler.timeSource, scope))
            val failure = shouldThrow<IllegalStateException> { limiter.quick() }
            failure.message shouldContain "cancelled with the scope"
            limiter.snapshot().failed shouldBe 1L
            limiter.snapshot().detachedInFlight shouldBe 0
        }
    }

    test("a soft call keeps the context elements of its caller but not its job") {
        withScope { scope ->
            val limiter = softLimiter(testEnvironment(testCoroutineScheduler.timeSource, scope))
            val callerJob = currentCoroutineContext()[Job]
            withContext(CoroutineName(CHECKOUT)) {
                val seen = limiter.execute { currentCoroutineContext()[CoroutineName]?.name }
                seen shouldBe CHECKOUT
            }
            val detachedJob = limiter.execute { currentCoroutineContext()[Job] }
            detachedJob shouldNotBe callerJob
        }
    }

    test("a soft limiter needs the scope of its environment") {
        shouldThrow<IllegalArgumentException> { softLimiter(testEnvironment(testCoroutineScheduler.timeSource)) }
    }

    test("a soft limiter with an infinite timeout runs the call in the coroutine of its caller") {
        withScope { scope ->
            val limiter = timeLimiter(LIMITER, testEnvironment(testCoroutineScheduler.timeSource, scope)) {
                timeout = Duration.INFINITE
                strategy = TimeoutStrategy.Soft()
            }
            val callerJob = currentCoroutineContext()[Job]
            limiter.execute { currentCoroutineContext()[Job] } shouldBe callerJob
            limiter.toString() shouldBe
                "TimeLimiter(payments, Infinity, Soft(isCancellingOnTimeout=true, maxDetached=1000))"
        }
    }
})
