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

package com.davils.kontinuity.core.fallback

import com.davils.kontinuity.core.CallTimeoutException
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.fixture.TestRejection
import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.time.Duration.Companion.seconds

private const val FAILURE: String = "failure"
private const val RUNTIME: String = "runtime"
private const val HANDLERS: Int = 3

@OptIn(ExperimentalKotest::class)
class FallbackTest : FunSpec({
    coroutineTestScope = true

    test("a successful call needs no fallback") {
        fallback<String>("user") { otherwise { "fallback" } }.execute { "call" } shouldBe "call"
    }

    test("the first handler that selects the failure runs") {
        val fallback = fallback<String>("user") {
            on<IllegalArgumentException> { failure -> "argument ${failure.message.orEmpty()}" }
            on<RuntimeException> { _ -> RUNTIME }
            otherwise { _ -> "otherwise" }
        }
        fallback.execute { throw IllegalArgumentException("x") } shouldBe "argument x"
        fallback.execute { throw IllegalStateException(FAILURE) } shouldBe RUNTIME
        fallback.execute { throw UnsupportedOperationException(FAILURE) } shouldBe RUNTIME
        fallback.size shouldBe HANDLERS
    }

    test("a rejection anywhere in the chain reaches onRejected") {
        val rejection = TestRejection()
        val fallback = fallback<Any> { onRejected { selected -> selected } }
        fallback.execute { throw IllegalStateException(rejection) } shouldBeSameInstanceAs rejection
    }

    test("a timeout anywhere in the chain reaches onTimeout") {
        val timeout = CallTimeoutException(InstanceName.of("slow"), 1.seconds)
        val fallback = fallback<Any>("user") { onTimeout { selected -> selected } }
        fallback.execute { throw IllegalStateException(timeout) } shouldBeSameInstanceAs timeout
    }

    test("a failure no handler selects is thrown unchanged") {
        val failure = IllegalStateException(FAILURE)
        val fallback = fallback<String>("user") { on<IllegalArgumentException> { _ -> "argument" } }
        shouldThrow<IllegalStateException> { fallback.execute { throw failure } } shouldBeSameInstanceAs failure
    }

    test("a handler that throws keeps the failure as suppressed") {
        val failure = IllegalStateException("call")
        val fallback = fallback<String>("user") { otherwise { _ -> throw UnsupportedOperationException("handler") } }
        val thrown = shouldThrow<UnsupportedOperationException> { fallback.recover(failure) }
        thrown.suppressedExceptions shouldHaveSize 1
        thrown.suppressedExceptions.single() shouldBeSameInstanceAs failure
    }

    test("a handler that rethrows the failure throws it unchanged") {
        val failure = IllegalStateException(FAILURE)
        val fallback = fallback<String>("user") { otherwise { selected -> throw selected } }
        val thrown = shouldThrow<IllegalStateException> { fallback.recover(failure) }
        thrown shouldBeSameInstanceAs failure
        thrown.suppressedExceptions shouldHaveSize 0
    }

    test("a fallback has from 1 to 64 handlers") {
        shouldThrow<ValidationException> { fallback<String>("user") {} }
        shouldThrow<ValidationException> {
            fallback<String>("user") { repeat(Fallback.HARD_HANDLERS + 1) { otherwise { _ -> "x" } } }
        }
    }

    test("a fallback has a name and a kind and shows them") {
        val fallback = fallback<String>("user.profile") { otherwise { _ -> "x" } }
        fallback.name shouldBe InstanceName.of("user.profile")
        fallback.kind shouldBe ResilienceKind.Fallback
        fallback.toString() shouldBe "Fallback(user.profile, handlers=1)"
    }

    test("a builder cannot change after it was evaluated") {
        val builder = FallbackBuilder<String>(InstanceName.of("user"))
        builder.otherwise { _ -> "x" }
        builder.build()
        shouldThrow<IllegalStateException> { builder.otherwise { _ -> "y" } }
    }
})

private fun <T> fallback(configure: FallbackBuilder<T>.() -> Unit): Fallback<T> = fallback("user", configure)
