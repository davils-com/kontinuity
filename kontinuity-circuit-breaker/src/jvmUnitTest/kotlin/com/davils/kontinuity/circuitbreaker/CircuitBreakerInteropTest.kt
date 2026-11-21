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

package com.davils.kontinuity.circuitbreaker

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

private const val OK: Int = 200
private const val BROKEN: String = "broken"
private const val WINDOW: Int = 3

class CircuitBreakerInteropTest : FunSpec({
    test("a blocking call is recorded by its outcome") {
        val breaker = breaker()
        breaker.executeBlocking { OK } shouldBe OK
        shouldThrow<IllegalStateException> { breaker.executeBlocking { error(BROKEN) } }
        shouldThrow<IllegalStateException> { breaker.executeBlocking { error(BROKEN) } }
        shouldThrow<CallNotPermittedException> { breaker.executeBlocking { OK } }
        breaker.snapshot().totals.successful shouldBe 1L
        breaker.snapshot().totals.failed shouldBe 2L
    }

    test("a completion stage is recorded when it completes") {
        val breaker = breaker()
        val completed = breaker.executeCompletionStage { CompletableFuture.completedFuture(OK) }
        completed.toCompletableFuture().get() shouldBe OK
        val failing = breaker.executeCompletionStage<Int> {
            CompletableFuture.supplyAsync { throw IllegalStateException(BROKEN) }
        }
        shouldThrow<ExecutionException> { failing.toCompletableFuture().get() }
        val throwing = breaker.executeCompletionStage<Int> { throw IllegalStateException(BROKEN) }
        shouldThrow<ExecutionException> { throwing.toCompletableFuture().get() }
        val refused = breaker.executeCompletionStage { CompletableFuture.completedFuture(OK) }
        val failure = shouldThrow<ExecutionException> { refused.toCompletableFuture().get() }
        failure.cause.shouldBeInstanceOf<CallNotPermittedException>()
        breaker.snapshot().totals.failed shouldBe 2L
    }

    test("a failure wrapped in a completion exception is recorded by its cause") {
        val breaker = circuitBreaker("wrapped") { failures { ignore<IllegalArgumentException>() } }
        val stage = breaker.executeCompletionStage<Int> {
            CompletableFuture.failedFuture(CompletionException(IllegalArgumentException(BROKEN)))
        }
        shouldThrow<ExecutionException> { stage.toCompletableFuture().get() }
        breaker.snapshot().totals.ignored shouldBe 1L
    }
})

private fun breaker(): CircuitBreaker = circuitBreaker("interop") {
    slidingWindow = countBased(WINDOW)
    minimumNumberOfCalls = WINDOW
}
