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

package com.davils.kontinuity.core

import com.davils.kontinuity.core.fixture.CountingStage
import com.davils.kontinuity.core.fixture.RefusingStage
import com.davils.kontinuity.core.fixture.TestRejection
import com.davils.kore.core.outcome.Outcome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val ANSWER: Int = 42
private const val CALLS: Int = 3
private val ELEMENTS: List<Int> = listOf(ANSWER, ANSWER + 1, ANSWER + CALLS)

@OptIn(ExperimentalKotest::class)
class StagesTest : FunSpec({
    coroutineTestScope = true

    test("the operator form runs the call through the stage") {
        val stage = CountingStage()
        stage { ANSWER } shouldBe ANSWER
        stage.calls shouldBe 1
    }

    test("attempt reports a result as a success") {
        CountingStage().attempt { ANSWER } shouldBe Outcome.Success(ANSWER)
    }

    test("attempt reports a failure of the call as failed") {
        val failure = IllegalStateException("broken")
        val outcome = CountingStage().attempt<Int> { throw failure }
        outcome shouldBe Outcome.Failure(CallFailure.Failed(failure))
    }

    test("attempt reports a refusal as rejected") {
        val outcome = RefusingStage().attempt { ANSWER }
        val failure = outcome.shouldBeInstanceOf<Outcome.Failure<CallFailure>>().failure
        failure.shouldBeInstanceOf<CallFailure.Rejected>().cause.shouldBeInstanceOf<TestRejection>()
    }

    test("attempt reports the timeout of a callee as a failure, not as a cancellation of the caller") {
        val outcome = CountingStage().attempt { withTimeout(1.milliseconds) { delay(1.seconds) } }
        val failure = outcome.shouldBeInstanceOf<Outcome.Failure<CallFailure>>().failure
        failure.shouldBeInstanceOf<CallFailure.Failed>().cause.shouldBeInstanceOf<IllegalStateException>()
    }

    test("decorated functions run through the stage on every call") {
        val stage = CountingStage()
        val answer = stage.decorate { ANSWER }
        val double = stage.decorateFunction { value: Int -> value * 2 }
        val sum = stage.decorateFunction { first: Int, second: Int -> first + second }
        answer() shouldBe ANSWER
        double(ANSWER) shouldBe ANSWER + ANSWER
        sum(ANSWER, 1) shouldBe ANSWER + 1
        stage.calls shouldBe CALLS
    }

    test("a flow through a stage is collected as one call") {
        val stage = CountingStage()
        val protected = ELEMENTS.asFlow().through(stage)
        protected.toList() shouldBe ELEMENTS
        stage.calls shouldBe 1
    }

    test("a flow through a refusing stage ends with the rejection") {
        val refused = ELEMENTS.asFlow().through(RefusingStage())
        shouldThrow<TestRejection> { refused.toList() }
    }
})
