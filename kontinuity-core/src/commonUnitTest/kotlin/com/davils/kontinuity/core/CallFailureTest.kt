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

import com.davils.kontinuity.core.fixture.TestRejection
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private const val SECRET: String = "secret"

class CallFailureTest : FunSpec({
    test("failures with the same cause are equal") {
        val cause = IllegalStateException(SECRET)
        CallFailure.Failed(cause) shouldBe CallFailure.Failed(cause)
        CallFailure.Failed(cause).hashCode() shouldBe cause.hashCode()
        CallFailure.Failed(cause) shouldNotBe CallFailure.Failed(IllegalStateException("other"))
        CallFailure.Failed(cause) shouldNotBe "Failed"
    }

    test("a failure shows the type of its cause but never its message") {
        CallFailure.Failed(IllegalStateException(SECRET)).toString() shouldBe "Failed(IllegalStateException)"
        CallFailure.Failed(object : RuntimeException(SECRET) {}).toString() shouldBe "Failed(Throwable)"
    }

    test("rejections with the same cause are equal and show their instance") {
        val rejection = TestRejection()
        CallFailure.Rejected(rejection) shouldBe CallFailure.Rejected(rejection)
        CallFailure.Rejected(rejection).hashCode() shouldBe rejection.hashCode()
        CallFailure.Rejected(rejection) shouldNotBe CallFailure.Rejected(TestRejection())
        CallFailure.Rejected(rejection) shouldNotBe CallFailure.Failed(rejection)
        CallFailure.Rejected(rejection).toString() shouldBe "Rejected(bulkhead refusing)"
    }

    test("every kind has an id") {
        ResilienceKind.entries.map { kind -> kind.id } shouldBe listOf(
            "circuit-breaker",
            "retry",
            "rate-limiter",
            "bulkhead",
            "dispatcher-bulkhead",
            "time-limiter",
            "cache",
            "hedge",
            "fallback",
            "policy",
        )
    }
})
