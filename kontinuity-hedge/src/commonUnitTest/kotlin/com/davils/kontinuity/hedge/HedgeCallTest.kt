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

import com.davils.kontinuity.hedge.fixture.DELAY
import com.davils.kontinuity.hedge.fixture.Script
import com.davils.kontinuity.hedge.fixture.fail
import com.davils.kontinuity.hedge.fixture.pass
import com.davils.kontinuity.hedge.fixture.quickHedge
import com.davils.kontinuity.testing.RefusingStage
import com.davils.kontinuity.testing.TestRejection
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SEVERAL: Int = 3
private const val FAST: Long = 50L
private const val QUICK: Long = 20L
private const val SLOWEST: Long = 500L
private const val BROKEN: String = "broken"

@OptIn(ExperimentalKotest::class)
class HedgeCallTest : FunSpec({
    coroutineTestScope = true

    test("a primary that answers before the delay wins and no copy starts") {
        val timeSource = testCoroutineScheduler.timeSource
        val hedge = quickHedge(testEnvironment(timeSource))
        val script = Script(pass(FAST.milliseconds))
        val mark = timeSource.markNow()
        hedge.execute { script.run() } shouldBe 0
        mark.elapsedNow() shouldBe FAST.milliseconds
        script.copies.calls shouldBe 1
        val snapshot = hedge.snapshot()
        snapshot.primarySucceeded shouldBe 1L
        snapshot.hedgesStarted shouldBe 0L
    }

    test("a hedged copy wins over a slow primary, which is cancelled") {
        val timeSource = testCoroutineScheduler.timeSource
        val hedge = quickHedge(testEnvironment(timeSource))
        val script = Script(pass(1.seconds), pass(FAST.milliseconds))
        val mark = timeSource.markNow()
        hedge.execute { script.run() } shouldBe 1
        mark.elapsedNow() shouldBe DELAY + FAST.milliseconds
        script.cancelled shouldBe 1
        val snapshot = hedge.snapshot()
        snapshot.hedgeSucceeded shouldBe 1L
        snapshot.hedgesStarted shouldBe 1L
    }

    test("several hedged copies start one delay apart and the fastest wins") {
        val timeSource = testCoroutineScheduler.timeSource
        val hedge = quickHedge(testEnvironment(timeSource)) { maxHedgedAttempts = SEVERAL }
        val log = recordEvents(hedge.events)
        val script = Script(pass(1.seconds), pass(1.seconds), pass(1.seconds), pass(FAST.milliseconds))
        val mark = timeSource.markNow()
        hedge.execute { script.run() } shouldBe SEVERAL
        mark.elapsedNow() shouldBe DELAY * SEVERAL + FAST.milliseconds
        script.cancelled shouldBe SEVERAL
        testCoroutineScheduler.runCurrent()
        log.stop()
        val started = log.only<HedgeEvent.HedgeStarted>()
        started.map { event -> event.attempt } shouldBe listOf(1, 2, SEVERAL)
        started.map { event -> event.after } shouldBe listOf(DELAY, DELAY * 2, DELAY * SEVERAL)
        log.only<HedgeEvent.HedgeSucceeded>().single().attempt shouldBe SEVERAL
    }

    test("a hedge never starts more copies than it allows") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource))
        val script = Script(pass(1.seconds), pass(1.seconds))
        hedge.execute { script.run() } shouldBe 0
        script.copies.calls shouldBe 2
        script.cancelled shouldBe 1
        hedge.snapshot().primarySucceeded shouldBe 1L
    }

    test("a recorded failure starts the next copy at once") {
        val timeSource = testCoroutineScheduler.timeSource
        val hedge = quickHedge(testEnvironment(timeSource))
        val log = recordEvents(hedge.events)
        val script = Script(fail(QUICK.milliseconds, IllegalStateException(BROKEN)), pass(FAST.milliseconds))
        val mark = timeSource.markNow()
        hedge.execute { script.run() } shouldBe 1
        mark.elapsedNow() shouldBe (QUICK + FAST).milliseconds
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.only<HedgeEvent.HedgeStarted>().single().after shouldBe QUICK.milliseconds
    }

    test("when every copy fails the failure of the primary is thrown with the others suppressed") {
        val timeSource = testCoroutineScheduler.timeSource
        val hedge = quickHedge(testEnvironment(timeSource)) { maxHedgedAttempts = 2 }
        val primary = IllegalStateException("primary")
        val first = IllegalArgumentException("first")
        val second = UnsupportedOperationException("second")
        val script = Script(
            fail(SLOWEST.milliseconds, primary),
            fail(FAST.milliseconds, first),
            fail(FAST.milliseconds, second),
        )
        val mark = timeSource.markNow()
        val thrown = shouldThrow<IllegalStateException> { hedge.execute { script.run() } }
        thrown shouldBeSameInstanceAs primary
        thrown.suppressedExceptions shouldBe listOf(first, second)
        mark.elapsedNow() shouldBe SLOWEST.milliseconds
        val snapshot = hedge.snapshot()
        snapshot.failed shouldBe 1L
        snapshot.hedgesStarted shouldBe 2L
    }

    test("a failure the filter ignores ends the call at once and cancels the other copies") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource)) {
            failures { ignore<IllegalArgumentException>() }
        }
        val script = Script(pass(1.seconds), fail(QUICK.milliseconds, IllegalArgumentException(BROKEN)))
        shouldThrow<IllegalArgumentException> { hedge.execute { script.run() } }
        script.cancelled shouldBe 1
        val early = Script(fail(QUICK.milliseconds, IllegalArgumentException(BROKEN)), pass(1.seconds))
        shouldThrow<IllegalArgumentException> { hedge.execute { early.run() } }
        early.copies.calls shouldBe 1
        hedge.snapshot().failed shouldBe 2L
    }

    test("a failure the filter passes ends the call at once") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource)) {
            failures { record<IllegalStateException>() }
        }
        val script = Script(fail(QUICK.milliseconds, UnsupportedOperationException(BROKEN)), pass(1.seconds))
        shouldThrow<UnsupportedOperationException> { hedge.execute { script.run() } }
        script.copies.calls shouldBe 1
    }

    test("a rejection is never hedged") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource))
        val refusing = RefusingStage()
        var copies = 0
        shouldThrow<TestRejection> {
            hedge.execute {
                copies += 1
                refusing.execute { BROKEN }
            }
        }
        copies shouldBe 1
        hedge.snapshot().failed shouldBe 1L
        hedge.snapshot().hedgesStarted shouldBe 0L
    }

    test("the cancellation of the caller cancels every copy and passes through") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource)) { maxHedgedAttempts = 2 }
        val log = recordEvents(hedge.events)
        val script = Script(pass(1.seconds), pass(1.seconds), pass(1.seconds))
        val call = async { hedge.execute { script.run() } }
        delay(DELAY * SEVERAL)
        call.cancel()
        shouldThrow<CancellationException> { call.await() }
        script.copies.calls shouldBe SEVERAL
        script.cancelled shouldBe SEVERAL
        hedge.snapshot().cancelled shouldBe 1L
        hedge.snapshot().failed shouldBe 0L
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.only<HedgeEvent.Cancelled>().size shouldBe 1
    }

    test("a cancellation that does not come from the caller is a failure of its copy") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource))
        val script = Script(fail(QUICK.milliseconds, CancellationException(BROKEN)), pass(FAST.milliseconds))
        hedge.execute { script.run() } shouldBe 1
        val lonely = Script(
            fail(QUICK.milliseconds, CancellationException(BROKEN)),
            fail(QUICK.milliseconds, CancellationException(BROKEN)),
        )
        val thrown = shouldThrow<IllegalStateException> { hedge.execute { lonely.run() } }
        thrown.cause.shouldBeInstanceOf<CancellationException>()
        hedge.snapshot().hedgeSucceeded shouldBe 1L
        hedge.snapshot().failed shouldBe 1L
    }

    test("a call returns the null result of its winner") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource))
        hedge.execute<String?> { null } shouldBe null
    }
})
