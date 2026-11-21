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
import com.davils.konfetti.testing.EventRecorder
import com.davils.konfetti.testing.testEventBus
import com.davils.kontinuity.circuitbreaker.fixture.WINDOW
import com.davils.kontinuity.circuitbreaker.fixture.fail
import com.davils.kontinuity.circuitbreaker.fixture.gauge
import com.davils.kontinuity.circuitbreaker.fixture.quietly
import com.davils.kontinuity.circuitbreaker.fixture.smallBreaker
import com.davils.kontinuity.circuitbreaker.fixture.succeed
import com.davils.kontinuity.circuitbreaker.fixture.sum
import com.davils.kontinuity.circuitbreaker.fixture.withScope
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val NAME: String = "payments"
private const val CALLS: String = "kontinuity.circuit_breaker.calls"
private const val FAILURE_RATE: Double = 75.0
private const val HALF: Double = 50.0

@OptIn(ExperimentalKotest::class)
class CircuitBreakerTelemetryTest : FunSpec({
    coroutineTestScope = true

    test("the events tell every outcome and every transition") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            failures { ignore<IllegalArgumentException>() }
        }
        val log = recordEvents(breaker.events)
        breaker.succeed()
        quietly { breaker.execute { throw IllegalArgumentException("ignored") } }
        breaker.fail(WINDOW)
        breaker.tryAcquirePermission()
        breaker.reset()
        breaker.acquirePermission().release()
        testCoroutineScheduler.runCurrent()
        log.stop()
        val shown = log.events.map(CircuitBreakerEvent::toString)
        shown shouldContain "Succeeded(payments, 0s, slow=false)"
        shown shouldContain "Ignored(payments, 0s)"
        shown shouldContain "Failed(payments, 0s, slow=false, IllegalStateException)"
        shown shouldContain "FailureRateExceeded(payments, 75.0%)"
        shown shouldContain "StateChanged(payments, Closed -> Open)"
        shown shouldContain "NotPermitted(payments, Open)"
        shown shouldContain "StateChanged(payments, Open -> Closed)"
        shown shouldContain "Reset(payments)"
        shown shouldContain "Released(payments)"
        log.only<CircuitBreakerEvent.StateChanged>().first().from shouldBe CircuitState.Closed
    }

    test("slow calls are reported as slow") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource)) {
            slowCallDurationThreshold = 1.seconds
            slowCallRateThreshold = HALF
        }
        val log = recordEvents(breaker.events)
        repeat(WINDOW) { quietly { breaker.execute { delay(2.seconds) } } }
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.events.map(CircuitBreakerEvent::toString) shouldContain "SlowCallRateExceeded(payments, 100.0%)"
        log.only<CircuitBreakerEvent.Succeeded>().first().isSlow shouldBe true
    }

    test("a metrics-only breaker reports a crossed threshold once") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.transitionTo(CircuitState.MetricsOnly)
        val log = recordEvents(breaker.events)
        breaker.fail(WINDOW * 2)
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.only<CircuitBreakerEvent.FailureRateExceeded>().size shouldBe 1
    }

    test("the meters count calls, transitions and the window") {
        val registry = meterRegistry()
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource) { meters(registry) })
        breaker.succeed()
        breaker.fail(WINDOW)
        breaker.tryAcquirePermission()
        registry.sum(CALLS, NAME, "successful") shouldBe 1.0
        registry.sum(CALLS, NAME, "failed") shouldBe (WINDOW - 1).toDouble()
        registry.sum(CALLS, NAME, "not_permitted") shouldBe 2.0
        registry.sum("kontinuity.circuit_breaker.transitions", NAME, "Closed", "Open") shouldBe 1.0
        registry.gauge("kontinuity.circuit_breaker.failure_rate", NAME) shouldBe FAILURE_RATE
        registry.gauge("kontinuity.circuit_breaker.buffered_calls", NAME) shouldBe WINDOW.toDouble()
    }

    test("the events reach the topic of the event bus") {
        withScope { scope ->
            val bus = testEventBus(scope)
            val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource) { events(bus) })
            val recorder = EventRecorder<CircuitBreakerEvent>()
            bus[CircuitBreakerEvent.TOPIC].consume("recorder") { handle(recorder::record) }
            breaker.succeed()
            recorder.awaitCount(1)
            val event = recorder.payloads.single()
            event.kind shouldBe ResilienceKind.CircuitBreaker
        }
    }

    test("the health of a breaker follows its state") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        val indicator = breaker.healthIndicator()
        indicator.check().status shouldBe HealthStatus.Up
        breaker.transitionTo(CircuitState.HalfOpen)
        indicator.check().status shouldBe HealthStatus.Degraded
        breaker.transitionTo(CircuitState.ForcedOpen)
        indicator.check().status shouldBe HealthStatus.Down
    }

    test("a breaker shows its name and its state") {
        val breaker = smallBreaker(testEnvironment(testCoroutineScheduler.timeSource))
        breaker.toString() shouldBe "CircuitBreaker(payments, Closed)"
        breaker.kind shouldBe ResilienceKind.CircuitBreaker
        breaker.snapshot().toString() shouldBe "CircuitBreakerSnapshot(Closed, WindowSnapshot(failureRate=-1.0, " +
            "slowCallRate=-1.0, bufferedCalls=0, failedCalls=0, slowCalls=0, slowFailedCalls=0), " +
            "TotalsSnapshot(successful=0, failed=0, ignored=0, released=0, notPermitted=0))"
    }
})
