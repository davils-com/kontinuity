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

import com.davils.kore.testing.HostileFragments
import com.davils.kore.testing.isSafeToShow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private const val DOTTED: String = "payments.charge-card.v2_eu"

class InstanceNameTest : FunSpec({
    test("a name of dotted parts with dashes and underscores is valid") {
        val name = InstanceName.of(DOTTED)
        name.value shouldBe DOTTED
        name.toString() shouldBe DOTTED
        InstanceName.isValid(DOTTED) shouldBe true
        InstanceName.ofOrNull(DOTTED) shouldBe name
    }

    test("names that break the grammar are refused") {
        listOf("", "Payments", "1payments", "pay..ments", "payments.", "pay--ments", "pay ments").forEach { text ->
            InstanceName.ofOrNull(text) shouldBe null
            InstanceName.isValid(text) shouldBe false
        }
    }

    test("a name may be as long as the maximum and no longer") {
        val longest = "a".repeat(InstanceName.MAXIMUM_LENGTH)
        InstanceName.of(longest).value shouldBe longest
        InstanceName.ofOrNull(longest + "a") shouldBe null
    }

    test("a refused name is shown escaped in the message") {
        val hostile = HostileFragments.all.map { fragment -> "pay$fragment" }.filterNot(InstanceName::isValid)
        hostile.size shouldBe HostileFragments.all.size - 2
        hostile.forEach { text ->
            val refusal = shouldThrow<IllegalArgumentException> { InstanceName.of(text) }
            val message = refusal.message.orEmpty()
            message shouldContain "kontinuity instance name"
            isSafeToShow(message) shouldBe true
        }
    }
})
