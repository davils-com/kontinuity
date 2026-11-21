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

package com.davils.kontinuity.hedge

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

private const val OK: Int = 200
private const val BROKEN: String = "broken"
private const val WAIT_SECONDS: Long = 30L
private const val QUICK: Long = 20L
private const val THREADS: Int = 4

class HedgeInteropTest : FunSpec({
    test("a hedged completion stage completes with the first copy and cancels the losing future") {
        onScope { scope ->
            val hedge = interopHedge()
            val futures = CopyOnWriteArrayList<CompletableFuture<Int>>()
            val stage = hedge.executeCompletionStage(scope) {
                val future = CompletableFuture<Int>()
                futures += future
                if (futures.size == 2) future.complete(OK)
                future
            }
            stage.toCompletableFuture().get(WAIT_SECONDS, TimeUnit.SECONDS) shouldBe OK
            futures.size shouldBe 2
            futures.first().isCancelled shouldBe true
            hedge.snapshot().hedgeSucceeded shouldBe 1L
        }
    }

    test("a stage that fails or a block that throws is a failure of its copy") {
        onScope { scope ->
            val hedge = interopHedge()
            val failing = hedge.executeCompletionStage<Int>(scope) {
                CompletableFuture.failedFuture(IllegalStateException(BROKEN))
            }
            val failure = shouldThrow<ExecutionException> {
                failing.toCompletableFuture().get(
                    WAIT_SECONDS,
                    TimeUnit.SECONDS,
                )
            }
            failure.cause.shouldBeInstanceOf<IllegalStateException>()
            val throwing = hedge.executeCompletionStage<Int>(scope) { throw IllegalArgumentException(BROKEN) }
            val thrown = shouldThrow<ExecutionException> {
                throwing.toCompletableFuture().get(
                    WAIT_SECONDS,
                    TimeUnit.SECONDS,
                )
            }
            thrown.cause.shouldBeInstanceOf<IllegalArgumentException>()
            hedge.snapshot().failed shouldBe 2L
        }
    }

    test("cancelling the stage cancels every copy") {
        onScope { scope ->
            val hedge = interopHedge()
            val futures = CopyOnWriteArrayList<CompletableFuture<Int>>()
            val started = CountDownLatch(2)
            val stage = hedge.executeCompletionStage(scope) {
                val future = CompletableFuture<Int>()
                futures += future
                started.countDown()
                future
            }
            started.await(WAIT_SECONDS, TimeUnit.SECONDS) shouldBe true
            stage.toCompletableFuture().cancel(true)
            awaitCancelled(hedge)
            futures.all { future -> future.isCancelled } shouldBe true
        }
    }
})

private fun interopHedge(): Hedge = hedge("interop") { delay = fixedDelay(QUICK.milliseconds) }

private fun onScope(block: (CoroutineScope) -> Unit) {
    val pool = Executors.newFixedThreadPool(THREADS)
    pool.asCoroutineDispatcher().use { dispatcher ->
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            block(scope)
        } finally {
            scope.cancel()
        }
    }
}

private fun awaitCancelled(hedge: Hedge) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
    while (hedge.snapshot().cancelled == 0L && System.nanoTime() < deadline) Thread.onSpinWait()
    hedge.snapshot().cancelled shouldBe 1L
}
