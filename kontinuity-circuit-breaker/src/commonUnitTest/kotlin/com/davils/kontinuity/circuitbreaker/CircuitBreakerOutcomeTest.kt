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

package com.davils.kontinuity.circuitbreaker

import com.davils.kontinuity.circuitbreaker.fixture.WAIT
import com.davils.kontinuity.circuitbreaker.fixture.WINDOW
import com.davils.kontinuity.circuitbreaker.fixture.fail
import com.davils.kontinuity.circuitbreaker.fixture.quietly
import com.davils.kontinuity.circuitbreaker.fixture.smallBreaker
import com.davils.kontinuity.circuitbreaker.fixture.succeed
import com.davils.kontinuity.core.decorate
import com.davils.kontinuity.core.invoke
import com.davils.kontinuity.testing.RefusingStage
import com.davils.kontinuity.testing.TestRejection
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.seconds

private const val SERVER_ERROR: Int = 503
private const val OK: Int = 200
private const val BROKEN: String = "broken"
private const val CALLS: Long = 4L

@OptIn(ExperimentalKotest::class)
class CircuitBreakerOutcomeTest : FunSpec({
    coroutineTestScope = true

    test("ignored failures count as nothing and passed failures as successes") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            failures {
                record<IllegalStateException>()
                ignore<IllegalArgumentException>()
            }
        }
        repeat(WINDOW) { quietly { breaker.execute { throw IllegalArgumentException(BROKEN) } } }
        repeat(WINDOW) { quietly { breaker.execute { throw UnsupportedOperationException(BROKEN) } } }
        val snapshot = breaker.snapshot()
        snapshot.state shouldBe CircuitState.Closed
        snapshot.totals.ignored shouldBe WINDOW.toLong()
        snapshot.totals.successful shouldBe WINDOW.toLong()
    }

    test("rejections of other components never count") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        val inner = RefusingStage()
        repeat(WINDOW) { shouldThrow<TestRejection> { breaker { inner { OK } } } }
        breaker.state shouldBe CircuitState.Closed
        breaker.snapshot().totals.ignored shouldBe WINDOW.toLong()
    }

    test("results the filter accepts count as failures but are returned") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            results { failIf<Int> { status -> status >= SERVER_ERROR } }
        }
        repeat(WINDOW) { breaker.execute { SERVER_ERROR } shouldBe SERVER_ERROR }
        breaker.state shouldBe CircuitState.Open
    }

    test("a result filter that throws fails the call with its exception") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            results { failIf<Int> { _ -> throw UnsupportedOperationException(BROKEN) } }
        }
        shouldThrow<UnsupportedOperationException> { breaker.execute { OK } }
        breaker.snapshot().totals.failed shouldBe 1L
    }

    test("opening failures and results open the breaker at once") {
        val byFailure = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            openOnFailures { record<IllegalStateException>() }
        }
        byFailure.fail()
        byFailure.state shouldBe CircuitState.Open
        val byResult = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            openOnResults { failIf<Int> { status -> status == SERVER_ERROR } }
        }
        byResult.execute { OK }
        byResult.state shouldBe CircuitState.Closed
        byResult.execute { SERVER_ERROR }
        byResult.state shouldBe CircuitState.Open
    }

    test("an opening failure of a trial opens the breaker again") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            openOnFailures { record<IllegalStateException>() }
        }
        breaker.fail()
        delay(WAIT)
        breaker.fail()
        breaker.state shouldBe CircuitState.Open
    }

    test("the cancellation of a caller releases its permit without an outcome") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        val call = async { breaker.execute { delay(1.seconds) } }
        yield()
        call.cancel()
        shouldThrow<CancellationException> { call.await() }
        breaker.snapshot().totals.released shouldBe 1L
        breaker.snapshot().window.bufferedCalls shouldBe 0
    }

    test("an outcome of a call permitted before a transition counts only in the totals") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        val permit = breaker.acquirePermission()
        breaker.transitionTo(CircuitState.ForcedOpen)
        breaker.transitionTo(CircuitState.Closed)
        permit.onFailure(IllegalStateException(BROKEN))
        breaker.snapshot().window.bufferedCalls shouldBe 0
        breaker.snapshot().totals.failed shouldBe 1L
    }

    test("a permit is settled once") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        val permit = breaker.acquirePermission()
        permit.isSettled shouldBe false
        permit.onSuccess()
        permit.onFailure(IllegalStateException(BROKEN))
        permit.release()
        permit.isSettled shouldBe true
        permit.toString() shouldBe "CircuitBreakerPermit(Closed, settled=true)"
        breaker.snapshot().totals.successful shouldBe 1L
        breaker.snapshot().totals.failed shouldBe 0L
    }

    test("a permit measures from its last restart") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            slowCallDurationThreshold = 1.seconds
        }
        val permit = breaker.acquirePermission()
        delay(2.seconds)
        permit.restartClock()
        permit.elapsed shouldBe 0.seconds
        permit.onResult(OK)
        breaker.snapshot().window.slowCalls shouldBe 0
    }

    test("refused calls are counted") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.fail(WINDOW)
        repeat(WINDOW) { breaker.tryAcquirePermission() shouldBe null }
        breaker.snapshot().totals.notPermitted shouldBe WINDOW.toLong()
    }

    test("decorators, flows and the operator form run through the breaker") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker { OK } shouldBe OK
        val decorated = breaker.decorate { OK }
        decorated() shouldBe OK
        val protected = flowOf(OK, SERVER_ERROR).circuitBreaker(breaker)
        protected.toList() shouldBe listOf(OK, SERVER_ERROR)
        breaker.succeed()
        breaker.snapshot().totals.successful shouldBe CALLS
    }
})
