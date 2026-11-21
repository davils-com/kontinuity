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

package com.davils.kontinuity.hedge

import com.davils.kollect.core.meterRegistry
import com.davils.konfetti.testing.EventRecorder
import com.davils.konfetti.testing.testEventBus
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.hedge.fixture.DELAY
import com.davils.kontinuity.hedge.fixture.HEDGE
import com.davils.kontinuity.hedge.fixture.Script
import com.davils.kontinuity.hedge.fixture.count
import com.davils.kontinuity.hedge.fixture.fail
import com.davils.kontinuity.hedge.fixture.gauge
import com.davils.kontinuity.hedge.fixture.pass
import com.davils.kontinuity.hedge.fixture.quickHedge
import com.davils.kontinuity.hedge.fixture.quietly
import com.davils.kontinuity.hedge.fixture.sum
import com.davils.kontinuity.hedge.fixture.withScope
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import com.davils.kore.testing.HostileFragments
import com.davils.kore.testing.isSafeToShow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val CALLS: String = "kontinuity.hedge.calls"
private const val FAST: Long = 50L
private const val RECORDED: Long = 3L
private const val BROKEN: String = "broken"
private const val DELAY_IN_SECONDS: Double = 0.1
private val HOSTILE: String = HostileFragments.all.joinToString(separator = "")

@OptIn(ExperimentalKotest::class)
class HedgeTelemetryTest : FunSpec({
    coroutineTestScope = true

    test("the events tell every start of a copy and every outcome of a call") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource))
        val log = recordEvents(hedge.events)
        hedge.execute { delay(FAST.milliseconds) }
        val slow = Script(pass(1.seconds), pass(FAST.milliseconds))
        hedge.execute { slow.run() }
        val failing = Script(
            fail(FAST.milliseconds, IllegalStateException(BROKEN)),
            fail(FAST.milliseconds, IllegalStateException(BROKEN)),
        )
        quietly { hedge.execute { failing.run() } }
        val call = async { hedge.execute { delay(1.seconds) } }
        delay(FAST.milliseconds)
        call.cancel()
        testCoroutineScheduler.runCurrent()
        log.stop()
        val shown = log.events.map(HedgeEvent::toString)
        shown shouldContain "PrimarySucceeded(search, 50ms)"
        shown shouldContain "HedgeStarted(search, attempt=1, after=100ms)"
        shown shouldContain "HedgeSucceeded(search, attempt=1, 150ms)"
        shown shouldContain "HedgeStarted(search, attempt=1, after=50ms)"
        shown shouldContain "Failed(search, attempts=2, IllegalStateException)"
        shown shouldContain "Cancelled(search)"
        log.events.map { event -> event.kind }.toSet() shouldBe setOf(ResilienceKind.Hedge)
        log.only<HedgeEvent.PrimarySucceeded>().single().duration shouldBe FAST.milliseconds
        log.only<HedgeEvent.HedgeSucceeded>().single().duration shouldBe DELAY + FAST.milliseconds
        log.only<HedgeEvent.Failed>().single().attempts shouldBe 2
    }

    test("the meters count calls, hedges and durations and show the delay") {
        val registry = meterRegistry()
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource) { meters(registry) })
        hedge.execute { }
        val slow = Script(pass(1.seconds), pass(FAST.milliseconds))
        hedge.execute { slow.run() }
        quietly { hedge.execute { error(BROKEN) } }
        val call = async { hedge.execute { delay(1.seconds) } }
        delay(FAST.milliseconds)
        call.cancel()
        testCoroutineScheduler.runCurrent()
        registry.sum(CALLS, HEDGE, "primary_succeeded") shouldBe 1.0
        registry.sum(CALLS, HEDGE, "hedge_succeeded") shouldBe 1.0
        registry.sum(CALLS, HEDGE, "failed") shouldBe 1.0
        registry.sum(CALLS, HEDGE, "cancelled") shouldBe 1.0
        registry.sum("kontinuity.hedge.hedges", HEDGE) shouldBe 2.0
        registry.gauge("kontinuity.hedge.delay", HEDGE) shouldBe DELAY_IN_SECONDS
        registry.count("kontinuity.hedge.call.duration", HEDGE) shouldBe RECORDED
    }

    test("the events reach the topic of the event bus") {
        withScope { scope ->
            val bus = testEventBus(scope)
            val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource) { events(bus) })
            val recorder = EventRecorder<HedgeEvent>()
            bus[HedgeEvent.TOPIC].consume("recorder") { handle(recorder::record) }
            hedge.execute { }
            recorder.awaitCount(1)
            recorder.payloads.single().kind shouldBe ResilienceKind.Hedge
        }
    }

    test("no message of a failure reaches an event") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource))
        val log = recordEvents(hedge.events)
        shouldThrow<IllegalStateException> { hedge.execute { throw IllegalStateException(HOSTILE) } }
        quietly { hedge.execute { throw object : RuntimeException(HOSTILE) {} } }
        testCoroutineScheduler.runCurrent()
        log.stop()
        val shown = log.events.map(HedgeEvent::toString)
        shown shouldContain "Failed(search, attempts=2, Throwable)"
        shown.forEach { text ->
            text shouldNotContain HOSTILE
            isSafeToShow(text) shouldBe true
        }
    }

    test("a hedge shows its name, its delay and its totals") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource))
        hedge.toString() shouldBe "Hedge(search, delay=100ms)"
        hedge.kind shouldBe ResilienceKind.Hedge
        hedge.name.value shouldBe HEDGE
        hedge.snapshot().toString() shouldBe "HedgeSnapshot(primarySucceeded=0, hedgeSucceeded=0, failed=0, " +
            "cancelled=0, hedgesStarted=0, delay=100ms)"
    }
})
