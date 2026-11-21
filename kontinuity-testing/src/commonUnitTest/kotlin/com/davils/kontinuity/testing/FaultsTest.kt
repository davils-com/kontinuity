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

package com.davils.kontinuity.testing

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds

private const val ANSWER: Int = 42
private const val EVERY: Int = 3
private const val FIRST: Int = 2
private val LATENCY = 100.milliseconds

@OptIn(ExperimentalKotest::class)
class FaultsTest : FunSpec({
    coroutineTestScope = true

    test("a periodic fault fails every n-th call") {
        val faults = faults { fail(every = EVERY) }
        faults { ANSWER } shouldBe ANSWER
        faults.inject { ANSWER } shouldBe ANSWER
        shouldThrow<InjectedFailure> { faults { ANSWER } }.number shouldBe EVERY
        faults.calls shouldBe EVERY
    }

    test("the first calls or chosen calls can fail with a chosen failure") {
        val first = faults { failFirst(FIRST) }
        repeat(FIRST) { shouldThrow<InjectedFailure> { first { ANSWER } } }
        first { ANSWER } shouldBe ANSWER
        val chosen = faults { failCalls(listOf(FIRST)) { number -> UnsupportedOperationException("call $number") } }
        chosen { ANSWER } shouldBe ANSWER
        shouldThrow<UnsupportedOperationException> { chosen { ANSWER } }.message shouldBe "call 2"
        val plain = faults { failCalls(listOf(1)) }
        shouldThrow<InjectedFailure> { plain { ANSWER } }
    }

    test("every call waits the latency") {
        val faults = faults { latency = LATENCY }
        val start = testCoroutineScheduler.timeSource.markNow()
        faults { ANSWER }
        start.elapsedNow() shouldBe LATENCY
        faults.toString() shouldBe "Faults(latency=100ms, rules=0, calls=1)"
    }

    test("a period below 1 is refused") {
        shouldThrow<IllegalArgumentException> { faults { fail(every = 0) } }
        injectedFailure(EVERY).message shouldBe "Call 3 failed by a planned fault"
    }
})
