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

import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kore.core.failure.attempted
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val ANSWER: Int = 42
private const val BROKEN: String = "broken"

@OptIn(ExperimentalKotest::class)
class StagesTest : FunSpec({
    coroutineTestScope = true

    test("a counting stage counts calls, successes and failures") {
        val stage = CountingStage()
        stage.execute { ANSWER } shouldBe ANSWER
        attempted { stage.execute<Int> { error(BROKEN) } }
        stage.calls shouldBe 2
        stage.successes shouldBe 1
        stage.failures shouldBe 1
        stage.kind shouldBe ResilienceKind.Policy
        stage.toString() shouldBe "CountingStage(counting, calls=2, successes=1, failures=1)"
    }

    test("a refusing stage refuses every call with a test rejection") {
        val stage = RefusingStage(InstanceName.of("full"))
        val rejection = shouldThrow<TestRejection> { stage.execute { ANSWER } }
        rejection.instance shouldBe InstanceName.of("full")
        rejection.kind shouldBe ResilienceKind.Bulkhead
        rejection.message shouldBe "The bulkhead full refused the call"
        stage.toString() shouldBe "RefusingStage(full)"
        TestRejection().message shouldBe "The bulkhead refusing refused the call"
    }
})
