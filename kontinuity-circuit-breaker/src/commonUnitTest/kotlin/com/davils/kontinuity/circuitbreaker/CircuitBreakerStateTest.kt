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
import com.davils.kontinuity.circuitbreaker.fixture.smallBreaker
import com.davils.kontinuity.circuitbreaker.fixture.succeed
import com.davils.kontinuity.circuitbreaker.fixture.withScope
import com.davils.kontinuity.core.backoff.exponential
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val HALF: Int = 2
private const val HALF_RATE: Double = 50.0
private const val ALL: Double = 100.0

@OptIn(ExperimentalKotest::class)
class CircuitBreakerStateTest : FunSpec({
    coroutineTestScope = true

    test("a closed breaker opens when the failure rate reaches its threshold") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.succeed(HALF)
        breaker.fail(HALF - 1)
        breaker.state shouldBe CircuitState.Closed
        breaker.fail()
        breaker.state shouldBe CircuitState.Open
        breaker.snapshot().window.failureRate shouldBe HALF_RATE
    }

    test("a breaker computes no rate before the window holds the minimum of calls") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.fail(WINDOW - 1)
        breaker.state shouldBe CircuitState.Closed
        breaker.snapshot().window.failureRate shouldBe -1.0
    }

    test("an open breaker refuses calls until its wait is over") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.fail(WINDOW)
        val refusal = shouldThrow<CallNotPermittedException> { breaker.execute { "call" } }
        refusal.state shouldBe CircuitState.Open
        refusal.message shouldBe "The circuit breaker payments is Open and permits no further calls"
        delay(WAIT)
        breaker.execute { "trial" } shouldBe "trial"
        breaker.state shouldBe CircuitState.HalfOpen
    }

    test("reading the state never moves the breaker") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.fail(WINDOW)
        delay(WAIT * 2)
        breaker.state shouldBe CircuitState.Open
        breaker.snapshot().state shouldBe CircuitState.Open
    }

    test("successful trials close the breaker with an empty window") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.fail(WINDOW)
        delay(WAIT)
        breaker.succeed(HALF)
        breaker.state shouldBe CircuitState.Closed
        breaker.snapshot().window.bufferedCalls shouldBe 0
    }

    test("failed trials open the breaker again for a growing wait") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            waitInOpenState = exponential(initial = WAIT, multiplier = 2.0)
        }
        breaker.fail(WINDOW)
        delay(WAIT)
        breaker.fail(HALF)
        breaker.state shouldBe CircuitState.Open
        delay(WAIT)
        shouldThrow<CallNotPermittedException> { breaker.execute { } }
        delay(WAIT)
        breaker.succeed()
        breaker.state shouldBe CircuitState.HalfOpen
    }

    test("a half-open breaker lets no more calls through than its trials") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.fail(WINDOW)
        delay(WAIT)
        val first = breaker.acquirePermission()
        val second = breaker.acquirePermission()
        breaker.tryAcquirePermission() shouldBe null
        first.release()
        val third = breaker.acquirePermission()
        third.state shouldBe CircuitState.HalfOpen
        second.onSuccess()
        third.onSuccess()
        breaker.state shouldBe CircuitState.Closed
    }

    test("slow calls open the breaker when their rate reaches its threshold") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            slowCallDurationThreshold = 1.seconds
            slowCallRateThreshold = HALF_RATE
        }
        repeat(WINDOW) { breaker.execute { delay(2.seconds) } }
        breaker.state shouldBe CircuitState.Open
        breaker.snapshot().window.slowCallRate shouldBe ALL
    }

    test("a time-based window forgets the calls older than its length") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            slidingWindow = timeBased(length = WAIT)
        }
        breaker.fail(WINDOW - 1)
        delay(WAIT + 1.seconds)
        breaker.fail()
        breaker.state shouldBe CircuitState.Closed
        breaker.snapshot().window.bufferedCalls shouldBe 1
    }

    test("a half-open breaker that waits too long moves to its configured state") {
        val reopening = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            maxWaitInHalfOpenState = 1.seconds
        }
        reopening.fail(WINDOW)
        delay(WAIT)
        reopening.acquirePermission().release()
        delay(2.seconds)
        reopening.tryAcquirePermission() shouldBe null
        reopening.state shouldBe CircuitState.Open
        val closing = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            maxWaitInHalfOpenState = 1.seconds
            stateAfterHalfOpenTimeout = CircuitState.Closed
        }
        closing.fail(WINDOW)
        delay(WAIT)
        closing.succeed()
        delay(2.seconds)
        closing.succeed()
        closing.state shouldBe CircuitState.Closed
    }

    test("the automatic transition moves an open breaker without a call") {
        val timeSource = testCoroutineScheduler.timeSource
        withScope { scope ->
            val breaker = smallBreaker(testEnvironment(timeSource, scope = scope)) {
                isAutomaticTransitionEnabled = true
                maxWaitInHalfOpenState = 1.seconds
            }
            breaker.fail(WINDOW)
            delay(WAIT + 1.milliseconds)
            breaker.state shouldBe CircuitState.HalfOpen
            delay(1.seconds)
            breaker.state shouldBe CircuitState.Open
        }
    }

    test("the automatic transition needs a scope") {
        shouldThrow<IllegalArgumentException> {
            smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) { isAutomaticTransitionEnabled = true }
        }
    }

    test("manual transitions reach every state") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.transitionTo(CircuitState.Closed) shouldBe false
        breaker.transitionTo(CircuitState.ForcedOpen) shouldBe true
        shouldThrow<CallNotPermittedException> { breaker.execute { } }.state shouldBe CircuitState.ForcedOpen
        breaker.transitionTo(CircuitState.Disabled)
        breaker.fail(WINDOW * 2)
        breaker.state shouldBe CircuitState.Disabled
        breaker.snapshot().window.bufferedCalls shouldBe 0
        breaker.transitionTo(CircuitState.Open)
        breaker.tryAcquirePermission() shouldBe null
        breaker.transitionTo(CircuitState.HalfOpen)
        breaker.succeed(HALF)
        breaker.state shouldBe CircuitState.Closed
    }

    test("a metrics-only breaker records but never opens") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.transitionTo(CircuitState.MetricsOnly)
        breaker.fail(WINDOW * 2)
        breaker.state shouldBe CircuitState.MetricsOnly
        breaker.snapshot().window.failureRate shouldBe ALL
        breaker.succeed(WINDOW)
        breaker.fail(WINDOW)
        breaker.state shouldBe CircuitState.MetricsOnly
    }

    test("a reset closes the breaker with an empty window") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.fail(WINDOW)
        breaker.reset()
        breaker.state shouldBe CircuitState.Closed
        breaker.snapshot().window.bufferedCalls shouldBe 0
        breaker.snapshot().totals.failed shouldBe WINDOW.toLong()
        breaker.reset()
        breaker.state shouldBe CircuitState.Closed
    }
})
