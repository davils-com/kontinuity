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

package com.davils.kontinuity.timelimiter

import com.davils.kontinuity.core.CallTimeoutException
import com.davils.kontinuity.core.environment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

private const val RESULT: String = "paid"
private const val FUTURE: String = "future"
private const val BLOCKING: String = "blocking"
private const val BROKEN: String = "broken call"
private const val WAIT_SECONDS: Long = 30L
private const val POLL_MILLIS: Long = 5L
private val TIMEOUT = 50.milliseconds

class TimeLimiterInteropTest : FunSpec({
    test("a future that completes in time returns its result") {
        onExecutor { dispatcher, _ ->
            val limiter = timeLimiter(FUTURE) { timeout = TIMEOUT }
            limiter.executeFuture { CompletableFuture.supplyAsync({ RESULT }, dispatcher.executor) } shouldBe RESULT
            limiter.snapshot().successful shouldBe 1L
        }
    }

    test("a future that fails is a failure of the call") {
        onExecutor { _, _ ->
            val limiter = timeLimiter(FUTURE) { timeout = TIMEOUT }
            shouldThrow<IOException> {
                limiter.executeFuture {
                    CompletableFuture.failedFuture<String>(
                        IOException(BROKEN),
                    )
                }
            }
            limiter.snapshot().failed shouldBe 1L
        }
    }

    test("a hard timeout cancels the future") {
        onExecutor { _, _ ->
            val limiter = timeLimiter(FUTURE) { timeout = TIMEOUT }
            val future = CompletableFuture<String>()
            shouldThrow<CallTimeoutException> { limiter.executeFuture { future } }
            future.isCancelled shouldBe true
        }
    }

    test("a soft timeout cancels the future when the strategy cancels") {
        onExecutor { _, scope ->
            val limiter = softLimiter(scope, isCancellingOnTimeout = true)
            val future = CompletableFuture<String>()
            shouldThrow<CallTimeoutException> { limiter.executeFuture { future } }
            eventually { future.isCancelled }
            eventually { limiter.snapshot().detachedInFlight == 0 }
        }
    }

    test("a soft timeout leaves the future running when the strategy does not cancel") {
        onExecutor { _, scope ->
            val limiter = softLimiter(scope, isCancellingOnTimeout = false)
            val future = CompletableFuture<String>()
            shouldThrow<CallTimeoutException> { limiter.executeFuture { future } }
            future.isCancelled shouldBe false
            limiter.snapshot().detachedInFlight shouldBe 1
            future.complete(RESULT)
            eventually { limiter.snapshot().detachedInFlight == 0 }
            limiter.snapshot().detachedFailed shouldBe 0L
        }
    }

    test("a future cancelled by someone else is a failure") {
        onExecutor { _, _ ->
            val limiter = timeLimiter(FUTURE) { timeout = TIMEOUT * 2 }
            val future = CompletableFuture<String>()
            future.cancel(false)
            shouldThrow<IllegalStateException> { limiter.executeFuture { future } }
            limiter.snapshot().failed shouldBe 1L
        }
    }

    test("a blocking call that returns in time returns its result") {
        onExecutor { dispatcher, _ ->
            val limiter = timeLimiter(BLOCKING) { timeout = TIMEOUT * 2 }
            limiter.executeBlocking(dispatcher) { RESULT } shouldBe RESULT
        }
    }

    test("a hard timeout interrupts a blocking call and leaves its thread clean") {
        onExecutor { dispatcher, _ ->
            val limiter = timeLimiter(BLOCKING) { timeout = TIMEOUT }
            val interrupt = AtomicReference<InterruptedException?>(null)
            shouldThrow<CallTimeoutException> {
                limiter.executeBlocking(dispatcher) {
                    try {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(WAIT_SECONDS))
                    } catch (expected: InterruptedException) {
                        interrupt.set(expected)
                    }
                }
            }
            (interrupt.get() != null) shouldBe true
            val flag = CompletableFuture.supplyAsync({ Thread.currentThread().isInterrupted }, dispatcher.executor)
            flag.await() shouldBe false
            limiter.snapshot().timedOut shouldBe 1L
        }
    }

    test("a soft timeout without cancelling leaves a blocking call running") {
        onExecutor { dispatcher, scope ->
            val limiter = softLimiter(scope, isCancellingOnTimeout = false)
            val release = CountDownLatch(1)
            val isInterrupted = AtomicBoolean(true)
            shouldThrow<CallTimeoutException> {
                limiter.executeBlocking(dispatcher) {
                    release.await(WAIT_SECONDS, TimeUnit.SECONDS)
                    isInterrupted.set(Thread.currentThread().isInterrupted)
                }
            }
            release.countDown()
            eventually { limiter.snapshot().detachedInFlight == 0 }
            isInterrupted.get() shouldBe false
        }
    }
})

private fun softLimiter(scope: CoroutineScope, isCancellingOnTimeout: Boolean): TimeLimiter =
    timeLimiter("soft", environment { scope(scope) }) {
        timeout = TIMEOUT
        strategy = TimeoutStrategy.Soft(isCancellingOnTimeout = isCancellingOnTimeout)
    }

private suspend fun onExecutor(block: suspend (ExecutorCoroutineDispatcher, CoroutineScope) -> Unit) {
    val executor = Executors.newSingleThreadExecutor()
    executor.asCoroutineDispatcher().use { dispatcher ->
        val scope = CoroutineScope(Job() + dispatcher)
        try {
            block(dispatcher, scope)
        } finally {
            scope.cancel()
        }
    }
}

private suspend fun eventually(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
    while (!condition()) {
        check(System.nanoTime() < deadline) { "The condition did not hold in time" }
        delay(POLL_MILLIS)
    }
}
