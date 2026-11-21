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

import com.davils.kontinuity.core.fixture.TestRejection
import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private const val FAILURE: String = "failure"

class FailureFilterTest : FunSpec({
    test("the default filter records every failure but rejections") {
        FailureFilter.RecordAll.verdictOf(IllegalStateException(FAILURE)) shouldBe FailureVerdict.Recorded
        FailureFilter.RecordAll.verdictOf(TestRejection()) shouldBe FailureVerdict.Ignored
        FailureFilter.RecordAll.verdictOf(RuntimeException(TestRejection())) shouldBe FailureVerdict.Ignored
        FailureFilter.RecordAll.rules shouldBe 0
    }

    test("a filter with record rules passes what they do not match") {
        val filter = failures { record<IllegalStateException>() }
        filter.verdictOf(IllegalStateException(FAILURE)) shouldBe FailureVerdict.Recorded
        filter.verdictOf(IllegalArgumentException(FAILURE)) shouldBe FailureVerdict.Passed
    }

    test("ignore rules win over record rules") {
        val filter = failures {
            record<RuntimeException>()
            ignore<IllegalArgumentException>()
        }
        filter.verdictOf(IllegalArgumentException(FAILURE)) shouldBe FailureVerdict.Ignored
        filter.verdictOf(IllegalStateException(FAILURE)) shouldBe FailureVerdict.Recorded
        filter.rules shouldBe 2
    }

    test("type rules look at the causes unless told not to") {
        val nested = RuntimeException(FAILURE, IllegalArgumentException(FAILURE))
        failures { ignore<IllegalArgumentException>() }.verdictOf(nested) shouldBe FailureVerdict.Ignored
        val flat = failures {
            ignore<IllegalArgumentException>()
            isCauseChainSearched = false
        }
        flat.verdictOf(nested) shouldBe FailureVerdict.Recorded
        flat.isCauseChainSearched shouldBe false
    }

    test("predicate rules decide by the failure") {
        val filter = failures {
            recordIf { failure -> failure.message == "record" }
            ignoreIf { failure -> failure.message == "ignore" }
        }
        filter.verdictOf(IllegalStateException("record")) shouldBe FailureVerdict.Recorded
        filter.verdictOf(IllegalStateException("ignore")) shouldBe FailureVerdict.Ignored
        filter.verdictOf(IllegalStateException("other")) shouldBe FailureVerdict.Passed
    }

    test("a predicate that throws records the failure and keeps its exception as suppressed") {
        val filter = failures { ignoreIf { _ -> error("broken predicate") } }
        val failure = IllegalArgumentException("call")
        filter.verdictOf(failure) shouldBe FailureVerdict.Recorded
        failure.suppressedExceptions shouldHaveSize 1
        failure.suppressedExceptions.single().shouldBeInstanceOf<IllegalStateException>()
    }

    test("a filter has at most 64 rules") {
        failures {
            repeat(
                FailureFilter.HARD_RULES,
            ) { record<IllegalStateException>() }
        }.rules shouldBe FailureFilter.HARD_RULES
        shouldThrow<ValidationException> {
            failures { repeat(FailureFilter.HARD_RULES + 1) { record<IllegalStateException>() } }
        }
    }

    test("a filter shows how many rules it has") {
        val filter = failures {
            record<IllegalStateException>()
            ignore(IllegalArgumentException::class)
            record(UnsupportedOperationException::class)
        }
        filter.toString() shouldBe "FailureFilter(ignored=1, recorded=2, causes=true)"
    }

    test("a builder cannot change after it was evaluated") {
        val builder = FailureFilterBuilder()
        builder.build()
        shouldThrow<IllegalStateException> { builder.record<IllegalStateException>() }
        shouldThrow<IllegalStateException> { builder.ignore<IllegalStateException>() }
        shouldThrow<IllegalStateException> { builder.recordIf { true } }
        shouldThrow<IllegalStateException> { builder.ignoreIf { true } }
        shouldThrow<IllegalStateException> { builder.isCauseChainSearched = false }
    }
})
