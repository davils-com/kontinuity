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

package com.davils.kontinuity.core.filter

import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val SERVER_ERROR: Int = 500

class ResultFilterTest : FunSpec({
    test("the empty filter makes no result a failure") {
        ResultFilter.None.isFailure(null) shouldBe false
        ResultFilter.None.isFailure("anything") shouldBe false
        ResultFilter.None.isEmpty shouldBe true
        ResultFilter.None.toString() shouldBe "ResultFilter(rules=0)"
    }

    test("a rule sees only results of its type") {
        val filter = results { failIf<Int> { status -> status >= SERVER_ERROR } }
        filter.isFailure(SERVER_ERROR) shouldBe true
        filter.isFailure(SERVER_ERROR - 1) shouldBe false
        filter.isFailure("500") shouldBe false
        filter.isFailure(null) shouldBe false
        filter.isEmpty shouldBe false
        filter.rules shouldBe 1
    }

    test("a null result fails only through failIfNull") {
        val filter = results { failIfNull() }
        filter.isFailure(null) shouldBe true
        filter.isFailure(1) shouldBe false
    }

    test("a rule that throws makes the check throw") {
        val filter = results { failIf<String> { _ -> error("broken rule") } }
        shouldThrow<IllegalStateException> { filter.isFailure("text") }
    }

    test("a filter has at most 64 rules") {
        shouldThrow<ValidationException> {
            results { repeat(FailureFilter.HARD_RULES + 1) { failIfNull() } }
        }
    }

    test("a builder cannot change after it was evaluated") {
        val builder = ResultFilterBuilder()
        builder.build()
        shouldThrow<IllegalStateException> { builder.failIfNull() }
        shouldThrow<IllegalStateException> { builder.failIf<Int> { true } }
    }
})
