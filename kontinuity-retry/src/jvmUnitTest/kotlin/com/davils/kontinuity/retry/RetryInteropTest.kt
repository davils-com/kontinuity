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

package com.davils.kontinuity.retry

import com.davils.kontinuity.core.backoff.fixed
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private const val OK: Int = 200
private const val FAILING: Int = -1
private const val BROKEN: String = "broken"
private const val ATTEMPTS: Int = 3
private const val TIMEOUT_SECONDS: Long = 10L

class RetryInteropTest : FunSpec({
    test("a blocking call is repeated until it succeeds or its attempts run out") {
        val retry = blockingRetry()
        val calls = AtomicInteger(0)
        retry.executeBlocking {
            check(calls.incrementAndGet() > 1) { BROKEN }
            OK
        } shouldBe OK
        shouldThrow<IllegalStateException> { retry.executeBlocking { error(BROKEN) } }
        retry.executeBlocking { OK } shouldBe OK
        val snapshot = retry.snapshot()
        snapshot.successfulWithRetry shouldBe 1L
        snapshot.failedWithRetry shouldBe 1L
        snapshot.successfulWithoutRetry shouldBe 1L
    }

    test("a blocking call returns or fails on its last result") {
        val retry = instantRetry { results { failIf<Int> { value -> value < 0 } } }
        retry.executeBlocking { FAILING } shouldBe FAILING
        val failing = instantRetry {
            results { failIf<Int> { value -> value < 0 } }
            isFailingAfterMaxAttempts = true
        }
        shouldThrow<RetriesExhaustedException> { failing.executeBlocking { FAILING } }.attempts shouldBe ATTEMPTS
    }

    test("an interrupt stops a blocking call, keeps the flag and counts as cancelled") {
        val retry = instantRetry { backoff = fixed(1.minutes) }
        Thread.currentThread().interrupt()
        shouldThrow<InterruptedException> { retry.executeBlocking { error(BROKEN) } }
        Thread.interrupted() shouldBe true
        shouldThrow<InterruptedException> { retry.executeBlocking<Int> { throw InterruptedException(BROKEN) } }
        Thread.interrupted() shouldBe true
        val sleeper = Thread.currentThread()
        val waker = Thread {
            while (sleeper.state != Thread.State.TIMED_WAITING) Thread.onSpinWait()
            sleeper.interrupt()
        }
        waker.start()
        shouldThrow<InterruptedException> { retry.executeBlocking { error(BROKEN) } }
        Thread.interrupted() shouldBe true
        waker.join()
        retry.snapshot().cancelled shouldBe ATTEMPTS.toLong()
    }

    test("a fatal error passes through and is counted once") {
        val retry = blockingRetry()
        shouldThrow<StackOverflowError> { retry.executeBlocking<Int> { throw StackOverflowError(BROKEN) } }
        retry.snapshot().failedWithoutRetry shouldBe 1L
        retry.snapshot().calls shouldBe 1L
    }

    test("a completion stage is started again until it succeeds") {
        onScope { scope ->
            val retry = blockingRetry()
            val calls = AtomicInteger(0)
            val stage = retry.executeCompletionStage(scope) {
                val attempt = calls.incrementAndGet()
                val succeeded = CompletableFuture.completedFuture(OK).takeIf { attempt >= ATTEMPTS }
                succeeded ?: CompletableFuture.failedFuture(IllegalStateException(BROKEN))
            }
            stage.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe OK
            calls.get() shouldBe ATTEMPTS
            val failing = retry.executeCompletionStage<Int>(scope) { error(BROKEN) }
            val failure = shouldThrow<ExecutionException> {
                failing.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            failure.cause.shouldBeInstanceOf<IllegalStateException>()
        }
    }

    test("a failure wrapped in a completion exception is judged by its cause") {
        onScope { scope ->
            val retry = instantRetry { failures { ignore<IllegalArgumentException>() } }
            val calls = AtomicInteger(0)
            val stage = retry.executeCompletionStage<Int>(scope) {
                calls.incrementAndGet()
                CompletableFuture.failedFuture(CompletionException(IllegalArgumentException(BROKEN)))
            }
            shouldThrow<ExecutionException> { stage.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            calls.get() shouldBe 1
        }
    }

    test("cancelling the stage stops the call and counts it as cancelled") {
        onScope { scope ->
            val retry = instantRetry { backoff = fixed(1.minutes) }
            val started = CountDownLatch(1)
            val stage = retry.executeCompletionStage<Int>(scope) {
                started.countDown()
                CompletableFuture.failedFuture(IllegalStateException(BROKEN))
            }
            started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
            stage.toCompletableFuture().cancel(true)
            awaitCancelled(retry)
            retry.snapshot().cancelled shouldBe 1L
        }
    }
})

private fun blockingRetry(): Retry = retry("interop") { backoff = fixed(1.milliseconds) }

private fun instantRetry(configure: RetryConfigBuilder.() -> Unit): Retry = retry("interop") {
    backoff = fixed(Duration.ZERO)
    configure()
}

private fun onScope(block: (CoroutineScope) -> Unit) {
    val executor = Executors.newSingleThreadExecutor()
    executor.asCoroutineDispatcher().use { dispatcher ->
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            block(scope)
        } finally {
            scope.cancel()
        }
    }
}

private fun awaitCancelled(retry: Retry) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
    while (retry.snapshot().cancelled == 0L && System.nanoTime() < deadline) Thread.sleep(1L)
}
