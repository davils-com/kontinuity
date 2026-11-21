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

import com.davils.kontinuity.core.fixture.CyclicFailure
import com.davils.kontinuity.core.fixture.TestRejection
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.time.Duration.Companion.seconds

private const val MAXIMUM_CAUSES: Int = 32

class RejectionsTest : FunSpec({
    test("a rejection in the cause chain is found") {
        val rejection = TestRejection()
        val failure = IllegalStateException("outer", RuntimeException("middle", rejection))
        failure.rejectionOrNull() shouldBeSameInstanceAs rejection
        rejection.rejectionOrNull() shouldBeSameInstanceAs rejection
    }

    test("a chain without a rejection finds none") {
        IllegalStateException("outer", RuntimeException("inner")).rejectionOrNull() shouldBe null
    }

    test("the walk stops after 32 causes") {
        val deepest = TestRejection()
        val chain = (1..MAXIMUM_CAUSES).fold<Int, Throwable>(deepest) { cause, _ -> RuntimeException(cause) }
        chain.causeChain() shouldHaveSize MAXIMUM_CAUSES
        chain.rejectionOrNull() shouldBe null
    }

    test("a cyclic chain is walked once") {
        val first = CyclicFailure()
        val second = CyclicFailure()
        first.next = second
        second.next = first
        first.causeChain() shouldBe listOf(first, second)
    }

    test("a rejection shows the kind and the name of its instance") {
        val rejection = TestRejection()
        rejection.kind shouldBe ResilienceKind.Bulkhead
        rejection.instance shouldBe InstanceName.of("refusing")
        rejection.message shouldBe "The bulkhead refusing is full"
    }

    test("a timeout names its time limiter and its timeout") {
        val timeout = CallTimeoutException(InstanceName.of("slow"), 2.seconds)
        timeout.message shouldBe "The time limiter slow cancelled a call after 2s"
    }
})
