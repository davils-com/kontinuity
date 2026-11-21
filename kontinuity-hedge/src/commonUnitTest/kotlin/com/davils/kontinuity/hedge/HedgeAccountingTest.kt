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
import com.davils.kontinuity.core.decorate
import com.davils.kontinuity.core.decorateFunction
import com.davils.kontinuity.hedge.fixture.HEDGE
import com.davils.kontinuity.hedge.fixture.Script
import com.davils.kontinuity.hedge.fixture.fail
import com.davils.kontinuity.hedge.fixture.pass
import com.davils.kontinuity.hedge.fixture.quickHedge
import com.davils.kontinuity.hedge.fixture.quietly
import com.davils.kontinuity.hedge.fixture.sum
import com.davils.kontinuity.testing.RefusingStage
import com.davils.kontinuity.testing.faults
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val ROUNDS: Int = 6
private const val THREE: Int = 3
private const val OUTCOMES: Int = 6
private const val FAST: Long = 50L
private const val ELEMENT: Int = 7
private const val BROKEN: String = "broken"
private const val CALLS: String = "kontinuity.hedge.calls"

@OptIn(ExperimentalKotest::class)
class HedgeAccountingTest : FunSpec({
    coroutineTestScope = true

    test("every call is counted exactly once, whatever its outcome") {
        val registry = meterRegistry()
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource) { meters(registry) }) {
            maxHedgedAttempts = 2
            failures { ignore<IllegalArgumentException>() }
        }
        repeat(ROUNDS) {
            hedge.execute { }
            val slow = Script(pass(1.seconds), pass(FAST.milliseconds))
            hedge.execute { slow.run() }
            quietly { hedge.execute { error(BROKEN) } }
            quietly { hedge.execute { throw IllegalArgumentException(BROKEN) } }
            quietly { hedge.execute { RefusingStage().execute { } } }
            val call = async { hedge.execute { delay(1.seconds) } }
            delay(FAST.milliseconds)
            call.cancel()
            testCoroutineScheduler.runCurrent()
        }
        val snapshot = hedge.snapshot()
        snapshot.calls shouldBe (ROUNDS * OUTCOMES).toLong()
        snapshot.primarySucceeded shouldBe ROUNDS.toLong()
        snapshot.hedgeSucceeded shouldBe ROUNDS.toLong()
        snapshot.failed shouldBe (ROUNDS * THREE).toLong()
        snapshot.cancelled shouldBe ROUNDS.toLong()
        val counted = listOf("primary_succeeded", "hedge_succeeded", "failed", "cancelled").sumOf { kind ->
            registry.sum(CALLS, HEDGE, kind)
        }
        counted shouldBe (ROUNDS * OUTCOMES).toDouble()
    }

    test("a hedge runs the copies of a fault plan until one succeeds") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource)) { maxHedgedAttempts = 2 }
        val plan = faults { failFirst(2) }
        hedge.execute { plan { ELEMENT } } shouldBe ELEMENT
        plan.calls shouldBe THREE
        hedge.snapshot().hedgeSucceeded shouldBe 1L
    }

    test("one failure is never suppressed by itself, twice or without bound") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource)) { maxHedgedAttempts = 2 }
        val same = IllegalStateException(BROKEN)
        shouldThrow<IllegalStateException> { hedge.execute { throw same } }.suppressedExceptions.shouldBeEmpty()
        val primary = IllegalStateException("primary")
        val other = IllegalStateException("other")
        val repeated = Script(
            fail(FAST.milliseconds, primary),
            fail(FAST.milliseconds, other),
            fail(FAST.milliseconds, other),
        )
        shouldThrow<IllegalStateException> { hedge.execute { repeated.run() } }.suppressedExceptions shouldBe listOf(
            other,
        )
        repeat(HedgeConfig.HARD_HEDGED_ATTEMPTS) {
            val script = Script(
                fail(FAST.milliseconds, primary),
                fail(FAST.milliseconds, IllegalStateException(BROKEN)),
                fail(FAST.milliseconds, IllegalStateException(BROKEN)),
            )
            shouldThrow<IllegalStateException> { hedge.execute { script.run() } } shouldBeSameInstanceAs primary
        }
        primary.suppressedExceptions.size shouldBe HedgeConfig.HARD_HEDGED_ATTEMPTS
    }

    test("the largest number of hedged copies starts every copy") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource)) {
            maxHedgedAttempts = HedgeConfig.HARD_HEDGED_ATTEMPTS
        }
        val plan = faults { failFirst(HedgeConfig.HARD_HEDGED_ATTEMPTS) }
        hedge.execute { plan { ELEMENT } } shouldBe ELEMENT
        plan.calls shouldBe HedgeConfig.HARD_HEDGED_ATTEMPTS + 1
        hedge.snapshot().hedgesStarted shouldBe HedgeConfig.HARD_HEDGED_ATTEMPTS.toLong()
        val exhausted = faults { failFirst(HedgeConfig.HARD_HEDGED_ATTEMPTS + 1) }
        quietly { hedge.execute { exhausted { ELEMENT } } }
        exhausted.calls shouldBe HedgeConfig.HARD_HEDGED_ATTEMPTS + 1
    }

    test("a hedge decorates functions") {
        val hedge = quickHedge(testEnvironment(testCoroutineScheduler.timeSource))
        hedge.decorate { ELEMENT }() shouldBe ELEMENT
        hedge.decorateFunction { value: Int -> value + 1 }(ELEMENT) shouldBe ELEMENT + 1
        hedge.snapshot().primarySucceeded shouldBe (THREE - 1).toLong()
    }
})
