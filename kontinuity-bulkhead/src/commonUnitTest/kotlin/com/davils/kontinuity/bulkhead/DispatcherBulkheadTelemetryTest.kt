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
import com.davils.kontinuity.bulkhead.fixture.withScope
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.registry.InstanceLimitException
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.time.Duration.Companion.seconds

private const val NAME: String = "reports"
private const val ORDERS: String = "orders"
private const val NARROW: String = "narrow"
private const val CALLS: String = "kontinuity.dispatcher_bulkhead.calls"
private const val RUNNING: String = "kontinuity.dispatcher_bulkhead.running"
private const val QUEUED: String = "kontinuity.dispatcher_bulkhead.queued"
private const val FIRST: Int = 1
private const val SECOND: Int = 2
private const val NARROW_CALLS: Int = 2
private val RUN = 3.seconds

@OptIn(ExperimentalKotest::class)
class DispatcherBulkheadTelemetryTest : FunSpec({
    coroutineTestScope = true

    test("the events tell every start, refusal and end") {
        val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
        val bulkhead = dispatcherBulkhead(NAME, dispatcher, testEnvironment(testCoroutineScheduler.timeSource)) {
            maxConcurrentCalls = 1
            queueCapacity = 0
        }
        val log = recordEvents(bulkhead.events)
        val caller = launch { bulkhead.execute { delay(RUN) } }
        testCoroutineScheduler.runCurrent()
        shouldThrow<BulkheadFullException> { bulkhead.execute { } }
        caller.join()
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.events.map(DispatcherBulkheadEvent::toString) shouldContainExactly listOf(
            "Permitted(reports, waited=0s)",
            "Rejected(reports, queueFull=true)",
            "Finished(reports, held=3s)",
        )
        log.events.map(DispatcherBulkheadEvent::kind).toSet() shouldBe setOf(ResilienceKind.DispatcherBulkhead)
    }

    test("the meters count every call and show the running and queued calls") {
        val registry = meterRegistry()
        val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
        val environment = testEnvironment(testCoroutineScheduler.timeSource) { meters(registry) }
        val bulkhead = dispatcherBulkhead(NAME, dispatcher, environment) {
            maxConcurrentCalls = 1
            queueCapacity = 1
        }
        val running = launch { bulkhead.execute { delay(RUN) } }
        testCoroutineScheduler.runCurrent()
        val queued = launch(start = CoroutineStart.UNDISPATCHED) { bulkhead.execute { } }
        registry.gauge(RUNNING, NAME) shouldBe 1.0
        registry.gauge(QUEUED, NAME) shouldBe 1.0
        shouldThrow<BulkheadFullException> { bulkhead.execute { } }
        queued.cancel()
        running.join()
        registry.sum(CALLS, NAME, "permitted") shouldBe 1.0
        registry.sum(CALLS, NAME, "rejected") shouldBe 1.0
        registry.sum(CALLS, NAME, "cancelled") shouldBe 1.0
        registry.sum(CALLS, NAME, "finished") shouldBe 1.0
        registry.count("kontinuity.dispatcher_bulkhead.wait", NAME) shouldBe 1L
        registry.gauge(RUNNING, NAME) shouldBe 0.0
        registry.gauge(QUEUED, NAME) shouldBe 0.0
    }

    test("the events reach the topic of the event bus") {
        withScope { scope ->
            val bus = testEventBus(scope)
            val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
            val environment = testEnvironment(testCoroutineScheduler.timeSource) { events(bus) }
            val bulkhead = dispatcherBulkhead(NAME, dispatcher, environment)
            val recorder = EventRecorder<DispatcherBulkheadEvent>()
            bus[DispatcherBulkheadEvent.TOPIC].consume("recorder") { handle(recorder::record) }
            bulkhead.execute { }
            recorder.awaitCount(2)
            recorder.payloads.first().shouldBeInstanceOf<DispatcherBulkheadEvent.Permitted>()
        }
    }

    test("a flow is collected on the dispatcher as one call") {
        val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
        val bulkhead = dispatcherBulkhead(NAME, dispatcher, testEnvironment(testCoroutineScheduler.timeSource))
        val protected = flowOf(FIRST, SECOND).dispatcherBulkhead(bulkhead)
        protected.toList() shouldBe listOf(FIRST, SECOND)
        bulkhead.snapshot().finished shouldBe 1L
    }

    test("a bulkhead and its snapshot show their state") {
        val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
        val bulkhead = dispatcherBulkhead(
            name = NAME,
            dispatcher = dispatcher,
            config = DispatcherBulkheadConfig.Default,
            environment = testEnvironment(testCoroutineScheduler.timeSource),
        )
        bulkhead.kind shouldBe ResilienceKind.DispatcherBulkhead
        bulkhead.toString() shouldBe "DispatcherBulkhead(reports, maxConcurrentCalls=8, queueCapacity=100)"
        bulkhead.snapshot().toString() shouldBe "DispatcherBulkheadSnapshot(running=0, queued=0, " +
            "remainingCapacity=108, permitted=0, rejected=0, finished=0, cancelled=0)"
    }

    test("a registry creates dispatcher bulkheads on its dispatcher once per name") {
        val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
        val registry = dispatcherBulkheadRegistry(dispatcher) {
            config(NARROW) { maxConcurrentCalls = NARROW_CALLS }
        }
        val reports = registry.dispatcherBulkhead(NAME)
        registry.dispatcherBulkhead(name = NAME, config = NARROW) shouldBeSameInstanceAs reports
        registry.dispatcherBulkhead(name = ORDERS, config = NARROW).config.maxConcurrentCalls shouldBe NARROW_CALLS
        registry.dispatcher shouldBeSameInstanceAs dispatcher
        registry.toString() shouldBe "DispatcherBulkheadRegistry(2 dispatcher bulkheads)"
        val custom = registry.dispatcherBulkhead("custom", base = NARROW) { queueCapacity = 1 }
        custom.config.maxConcurrentCalls shouldBe NARROW_CALLS
        registry.dispatcherBulkhead("custom") { queueCapacity = 0 } shouldBeSameInstanceAs custom
        val given = registry.dispatcherBulkhead("given", dispatcherBulkheadConfig { queueCapacity = 0 })
        given.config.queueCapacity shouldBe 0
        shouldThrow<IllegalArgumentException> { registry.dispatcherBulkhead("other", base = "missing") {} }
        registry.environment.scope shouldBe null
    }

    test("a full registry refuses new dispatcher bulkheads") {
        val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
        val registry = dispatcherBulkheadRegistry(dispatcher) { limits { maximumInstances = 1 } }
        registry.dispatcherBulkhead(NAME)
        shouldThrow<InstanceLimitException> { registry.dispatcherBulkhead(ORDERS) }
    }
})
