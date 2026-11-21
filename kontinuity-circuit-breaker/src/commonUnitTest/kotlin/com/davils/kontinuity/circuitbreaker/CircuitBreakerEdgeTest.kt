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

import com.davils.kollect.core.health.HealthStatus
import com.davils.kollect.core.meterRegistry
import com.davils.kontinuity.circuitbreaker.fixture.WAIT
import com.davils.kontinuity.circuitbreaker.fixture.WINDOW
import com.davils.kontinuity.circuitbreaker.fixture.fail
import com.davils.kontinuity.circuitbreaker.fixture.quietly
import com.davils.kontinuity.circuitbreaker.fixture.smallBreaker
import com.davils.kontinuity.circuitbreaker.fixture.succeed
import com.davils.kontinuity.circuitbreaker.fixture.sum
import com.davils.kontinuity.circuitbreaker.fixture.withScope
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val NAME: String = "payments"
private const val CALLS: String = "kontinuity.circuit_breaker.calls"
private const val HALF_RATE: Double = 50.0
private const val OK: Int = 200
private const val TWO: Int = 2

@OptIn(ExperimentalKotest::class)
class CircuitBreakerEdgeTest : FunSpec({
    coroutineTestScope = true

    test("slow failures count as failed and slow in a count-based and a time-based window") {
        listOf(countBased(WINDOW), timeBased(WAIT)).forEach { window ->
            val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
                slidingWindow = window
                slowCallDurationThreshold = 1.seconds
            }
            quietly {
                breaker.execute {
                    delay(TWO.seconds)
                    slowFailure()
                }
            }
            breaker.execute { delay(TWO.seconds) }
            val snapshot = breaker.snapshot().window
            snapshot.slowFailedCalls shouldBe 1
            snapshot.slowCalls shouldBe TWO
            snapshot.failedCalls shouldBe 1
        }
    }

    test("a count-based window forgets its oldest call") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.fail()
        breaker.succeed(WINDOW)
        breaker.snapshot().window.failedCalls shouldBe 0
    }

    test("a metrics-only breaker reports a crossed rate of slow calls") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            slowCallDurationThreshold = 1.seconds
            slowCallRateThreshold = HALF_RATE
        }
        breaker.transitionTo(CircuitState.MetricsOnly)
        val log = recordEvents(breaker.events)
        repeat(WINDOW) { breaker.execute { delay(TWO.seconds) } }
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.only<CircuitBreakerEvent.SlowCallRateExceeded>().size shouldBe 1
    }

    test("a permit can be asked for without throwing") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            openOnResults { failIf<Int> { _ -> error("broken opening rule") } }
        }
        val permit = breaker.tryAcquirePermission()
        permit shouldNotBe null
        permit?.onResult(OK)
        breaker.state shouldBe CircuitState.Closed
        breaker.snapshot().totals.successful shouldBe 1L
    }

    test("a disabled or metrics-only breaker is healthy") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.transitionTo(CircuitState.Disabled)
        breaker.healthIndicator().check().status shouldBe HealthStatus.Up
        breaker.transitionTo(CircuitState.MetricsOnly)
        breaker.healthIndicator().check().status shouldBe HealthStatus.Up
    }

    test("ignored and released calls are metered") {
        val registry = meterRegistry()
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource) { meters(registry) }) {
            failures { ignore<IllegalStateException>() }
        }
        breaker.fail()
        breaker.acquirePermission().release()
        registry.sum(CALLS, NAME, "ignored") shouldBe 1.0
        registry.sum(CALLS, NAME, "released") shouldBe 1.0
    }

    test("a breaker can be created with the defaults") {
        val breaker = circuitBreaker(NAME)
        breaker.config.toString() shouldBe CircuitBreakerConfig.Default.toString()
        countBased(WINDOW) shouldNotBe countBased(WINDOW + 1)
    }

    test("the automatic transition stops when the breaker closes and waits for trials without a deadline") {
        val timeSource = testCoroutineScheduler.timeSource
        withScope { scope ->
            val breaker = smallBreaker(testEnvironment(timeSource, scope = scope)) {
                isAutomaticTransitionEnabled = true
            }
            breaker.fail(WINDOW)
            delay(WAIT + 1.milliseconds)
            breaker.state shouldBe CircuitState.HalfOpen
            breaker.succeed(TWO)
            delay(WAIT * TWO)
            breaker.state shouldBe CircuitState.Closed
        }
    }
})

private fun slowFailure(): Nothing = error("slow failure")
