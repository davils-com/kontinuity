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

import com.davils.kollect.core.meterRegistry
import com.davils.konfetti.testing.EventRecorder
import com.davils.konfetti.testing.testEventBus
import com.davils.kontinuity.bulkhead.fixture.count
import com.davils.kontinuity.bulkhead.fixture.gauge
import com.davils.kontinuity.bulkhead.fixture.sum
import com.davils.kontinuity.bulkhead.fixture.waitingBulkhead
import com.davils.kontinuity.bulkhead.fixture.withScope
import com.davils.kontinuity.core.CallRejectedException
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val NAME: String = "payments"
private const val CALLS: String = "kontinuity.bulkhead.calls"
private const val AVAILABLE: String = "kontinuity.bulkhead.available_concurrent_calls"
private const val WAITING: String = "kontinuity.bulkhead.waiting_calls"
private const val FIRST: Int = 1
private const val SECOND: Int = 2
private const val THIRD: Int = 3
private val HELD = 2.seconds

@OptIn(ExperimentalKotest::class)
class BulkheadTelemetryTest : FunSpec({
    coroutineTestScope = true

    test("the events tell every permit, refusal and release") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        val log = recordEvents(bulkhead.events)
        val holder = bulkhead.acquirePermission()
        launch {
            delay(HELD)
            holder.release()
        }
        bulkhead.tryAcquirePermission()
        bulkhead.acquirePermission().release()
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.events.map(BulkheadEvent::toString) shouldContainExactly listOf(
            "Permitted(payments, waited=0s)",
            "Rejected(payments, queueFull=false)",
            "Finished(payments, held=2s)",
            "Permitted(payments, waited=2s)",
            "Finished(payments, held=0s)",
        )
        log.events.map(BulkheadEvent::kind).toSet() shouldBe setOf(ResilienceKind.Bulkhead)
    }

    test("the meters count every decision and show the permits and the queue") {
        val registry = meterRegistry()
        val environment = testEnvironment(testCoroutineScheduler.timeSource) { meters(registry) }
        val bulkhead = waitingBulkhead(environment) { maxWaitingCalls = 1 }
        registry.gauge(AVAILABLE, NAME) shouldBe 1.0
        val holder = bulkhead.acquirePermission()
        val waiter = launch { bulkhead.acquirePermission() }
        testCoroutineScheduler.runCurrent()
        registry.gauge(WAITING, NAME) shouldBe 1.0
        registry.gauge(AVAILABLE, NAME) shouldBe 0.0
        shouldThrow<BulkheadFullException> { bulkhead.acquirePermission() }
        waiter.cancel()
        testCoroutineScheduler.runCurrent()
        holder.release()
        registry.sum(CALLS, NAME, "permitted") shouldBe 1.0
        registry.sum(CALLS, NAME, "rejected") shouldBe 1.0
        registry.sum(CALLS, NAME, "cancelled") shouldBe 1.0
        registry.sum(CALLS, NAME, "finished") shouldBe 1.0
        registry.count("kontinuity.bulkhead.wait", NAME) shouldBe 1L
        registry.gauge(WAITING, NAME) shouldBe 0.0
        registry.gauge(AVAILABLE, NAME) shouldBe 1.0
        bulkhead.changeConfig(bulkheadConfig(bulkhead.config) { maxConcurrentCalls = THIRD })
        registry.gauge(AVAILABLE, NAME) shouldBe THIRD.toDouble()
    }

    test("the events reach the topic of the event bus") {
        withScope { scope ->
            val bus = testEventBus(scope)
            val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource) { events(bus) })
            val recorder = EventRecorder<BulkheadEvent>()
            bus[BulkheadEvent.TOPIC].consume("recorder") { handle(recorder::record) }
            bulkhead.execute { }
            recorder.awaitCount(2)
            recorder.payloads.first().shouldBeInstanceOf<BulkheadEvent.Permitted>()
        }
    }

    test("a refusal names the bulkhead and why it refused") {
        val bulkhead = bulkhead(NAME, testEnvironment(testCoroutineScheduler.timeSource)) { maxConcurrentCalls = 1 }
        val holder = bulkhead.acquirePermission()
        val refusal = shouldThrow<BulkheadFullException> { bulkhead.execute { } }
        refusal.shouldBeInstanceOf<CallRejectedException>()
        refusal.instance shouldBe bulkhead.name
        refusal.kind shouldBe ResilienceKind.Bulkhead
        refusal.message shouldBe "The bulkhead payments is full and no permit became free in time"
        holder.release()
        val waiting = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource)) { maxWaitingCalls = 0 }
        val other = waiting.acquirePermission()
        shouldThrow<BulkheadFullException> { waiting.execute { } }.message shouldBe
            "The bulkhead payments is full and its queue of waiting calls is full"
        other.release()
    }

    test("a hostile name is refused before a bulkhead exists") {
        shouldThrow<IllegalArgumentException> { bulkhead("pay\nments") }
        shouldThrow<IllegalArgumentException> { bulkhead("<script>", BulkheadConfig.Default) }
    }

    test("a flow is collected through the bulkhead as one call") {
        val bulkhead = bulkhead(NAME, testEnvironment(testCoroutineScheduler.timeSource)) { maxConcurrentCalls = 1 }
        val protected = flowOf(FIRST, SECOND, THIRD).bulkhead(bulkhead)
        protected.toList() shouldBe listOf(FIRST, SECOND, THIRD)
        bulkhead.snapshot().finished shouldBe 1L
        val holder = bulkhead.acquirePermission()
        val refused = flowOf(FIRST).bulkhead(bulkhead)
        shouldThrow<BulkheadFullException> { refused.toList() }
        holder.release()
    }

    test("a bulkhead and its snapshot show their state") {
        val bulkhead = waitingBulkhead(testEnvironment(testCoroutineScheduler.timeSource))
        bulkhead.kind shouldBe ResilienceKind.Bulkhead
        bulkhead.toString() shouldBe "Bulkhead(payments, maxConcurrentCalls=1)"
        bulkhead.snapshot().toString() shouldBe "BulkheadSnapshot(available=1, maxAllowed=1, concurrent=0, " +
            "waiting=0, permitted=0, rejected=0, finished=0, cancelled=0)"
    }
})
