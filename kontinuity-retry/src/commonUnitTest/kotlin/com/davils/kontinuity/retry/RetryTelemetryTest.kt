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

import com.davils.kollect.core.meterRegistry
import com.davils.konfetti.testing.EventRecorder
import com.davils.konfetti.testing.testEventBus
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.retry.fixture.ANSWER
import com.davils.kontinuity.retry.fixture.BROKEN
import com.davils.kontinuity.retry.fixture.HOSTILE
import com.davils.kontinuity.retry.fixture.Hostile
import com.davils.kontinuity.retry.fixture.RETRY
import com.davils.kontinuity.retry.fixture.broken
import com.davils.kontinuity.retry.fixture.quickRetry
import com.davils.kontinuity.retry.fixture.quietly
import com.davils.kontinuity.retry.fixture.recordings
import com.davils.kontinuity.retry.fixture.sum
import com.davils.kontinuity.retry.fixture.virtualEnvironment
import com.davils.kontinuity.retry.fixture.withScope
import com.davils.kontinuity.testing.faults
import com.davils.kontinuity.testing.recordEvents
import com.davils.kore.testing.isSafeToShow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CALLS: String = "kontinuity.retry.calls"
private const val FAILING: Int = -1
private const val RETRIES: Long = 6L

@OptIn(ExperimentalKotest::class)
class RetryTelemetryTest : FunSpec({
    coroutineTestScope = true

    test("the events tell every retry and every end of a call") {
        val retry = quickRetry(virtualEnvironment()) {
            failures { ignore<IllegalArgumentException>() }
            results { failIf<Int> { value -> value < 0 } }
        }
        val log = recordEvents(retry.events)
        retry.execute { ANSWER }
        val faults = faults { failFirst(1) }
        retry.execute { faults { ANSWER } }
        quietly { retry.execute { throw IllegalArgumentException(BROKEN) } }
        retry.execute { FAILING }
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.events.map(RetryEvent::toString) shouldContainExactly listOf(
            "Succeeded(payments, attempts=1)",
            "Retrying(payments, attempt=2, 1s, InjectedFailure)",
            "Succeeded(payments, attempts=2)",
            "IgnoredFailure(payments, IllegalArgumentException)",
            "Retrying(payments, attempt=2, 1s, result)",
            "Retrying(payments, attempt=3, 1s, result)",
            "Exhausted(payments, attempts=3, result)",
        )
        log.events.first().kind shouldBe ResilienceKind.Retry
        log.only<RetryEvent.Retrying>().first().reasonType shouldBe "InjectedFailure"
        log.only<RetryEvent.Exhausted>().single().failureType shouldBe "result"
        log.only<RetryEvent.IgnoredFailure>().single().failureType shouldBe "IllegalArgumentException"
        log.only<RetryEvent.Succeeded>().last().attempts shouldBe 2
    }

    test("a budget denial is an event of its own") {
        val environment = virtualEnvironment()
        val retry = quickRetry(environment) {
            budget = retryBudget(environment) {
                ratio = 0.0
                minRetriesPerSecond = 0.0
            }
        }
        val log = recordEvents(retry.events)
        quietly { retry.execute { broken() } }
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.events.map(RetryEvent::toString) shouldContainExactly listOf(
            "BudgetDenied(payments, attempt=2)",
            "Exhausted(payments, attempts=1, IllegalStateException)",
        )
    }

    test("the meters count calls by how they ended, the retries and the waits") {
        val registry = meterRegistry()
        val retry = quickRetry(virtualEnvironment { meters(registry) })
        retry.execute { ANSWER }
        val faults = faults { failFirst(1) }
        retry.execute { faults { ANSWER } }
        quietly { retry.execute { broken() } }
        quietly { retry.execute { throw IllegalArgumentException(BROKEN) } }
        withScope { scope ->
            val job = scope.launch { retry.execute { broken() } }
            delay(1)
            job.cancelAndJoin()
        }
        registry.sum(CALLS, RETRY, "successful_without_retry") shouldBe 1.0
        registry.sum(CALLS, RETRY, "successful_with_retry") shouldBe 1.0
        registry.sum(CALLS, RETRY, "failed_with_retry") shouldBe 2.0
        registry.sum(CALLS, RETRY, "failed_without_retry") shouldBe 0.0
        registry.sum(CALLS, RETRY, "cancelled") shouldBe 1.0
        registry.sum("kontinuity.retry.retries", RETRY) shouldBe RETRIES.toDouble()
        registry.recordings("kontinuity.retry.backoff", RETRY) shouldBe RETRIES
    }

    test("the events reach the topic of the event bus") {
        withScope { scope ->
            val bus = testEventBus(scope)
            val retry = quickRetry(virtualEnvironment { events(bus) })
            val recorder = EventRecorder<RetryEvent>()
            bus[RetryEvent.TOPIC].consume("recorder") { handle(recorder::record) }
            retry.execute { ANSWER }
            recorder.awaitCount(1)
            recorder.payloads.single().kind shouldBe ResilienceKind.Retry
        }
    }

    test("no message of a failure and no value of a result reaches an event or an exception") {
        val retry = quickRetry(virtualEnvironment()) {
            results { failIf<Hostile> { _ -> true } }
            isFailingAfterMaxAttempts = true
        }
        val log = recordEvents(retry.events)
        quietly { retry.execute { throw IllegalStateException(HOSTILE) } }
        val exhausted = shouldThrow<RetriesExhaustedException> { retry.execute { Hostile() } }
        testCoroutineScheduler.runCurrent()
        log.stop()
        val shown = log.events.map(RetryEvent::toString) + exhausted.message.orEmpty()
        shown.forEach { text ->
            text shouldNotContain HOSTILE
            isSafeToShow(text) shouldBe true
        }
        exhausted.resultType shouldBe "Hostile"
    }

    test("a retry shows its name and its attempts") {
        val retry = quickRetry(virtualEnvironment())
        retry.toString() shouldBe "Retry(payments, maxAttempts=3)"
        retry.kind shouldBe ResilienceKind.Retry
        retry.name.value shouldBe RETRY
        retry.config.maxAttempts shouldBe RetryConfig.Default.maxAttempts
    }

    test("a failure or a result of an anonymous type is named by its base") {
        val retry = quickRetry(virtualEnvironment()) {
            maxAttempts = 1
            results { failIf<Any> { value -> value !is Int } }
            isFailingAfterMaxAttempts = true
        }
        val log = recordEvents(retry.events)
        quietly { retry.execute { throw object : IllegalStateException(BROKEN) {} } }
        val exhausted = shouldThrow<RetriesExhaustedException> { retry.execute { object {} } }
        exhausted.resultType shouldBe "Any"
        val judging = quickRetry(virtualEnvironment()) {
            results { failIf<Int> { _ -> throw object : UnsupportedOperationException(BROKEN) {} } }
        }
        val judgingLog = recordEvents(judging.events)
        quietly { judging.execute<Int> { ANSWER } }
        testCoroutineScheduler.runCurrent()
        log.stop()
        judgingLog.stop()
        log.only<RetryEvent.Exhausted>().first().failureType shouldBe "Throwable"
        judgingLog.only<RetryEvent.IgnoredFailure>().single().failureType shouldBe "Throwable"
    }
})
