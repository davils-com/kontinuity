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

package com.davils.kontinuity.bulkhead

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val THREADS: Int = 8
private const val PARALLEL: Int = 2
private const val CALLS: Int = 40
private const val QUEUE: Int = 100
private const val OK: Int = 200
private const val SLEEP_MILLIS: Long = 2L
private const val TIMEOUT_SECONDS: Long = 30L
private const val DECIDED: Long = 3L

class DispatcherBulkheadInteropTest : FunSpec({
    test("real threads never run more blocking calls at once than the bulkhead allows") {
        val dispatcher = Executors.newFixedThreadPool(THREADS).asCoroutineDispatcher()
        dispatcher.use { pool -> parallel(pool) }
    }

    test("a refused, failing or cancelled submission completes its stage and spares the scope") {
        val dispatcher = Executors.newFixedThreadPool(THREADS).asCoroutineDispatcher()
        dispatcher.use { pool -> submissions(pool) }
    }
})

private fun parallel(pool: CoroutineDispatcher) {
    val scope = CoroutineScope(SupervisorJob())
    val bulkhead = dispatcherBulkhead("threads", pool) {
        maxConcurrentCalls = PARALLEL
        queueCapacity = QUEUE
    }
    val running = AtomicInteger(0)
    val highest = AtomicInteger(0)
    val stages = List(CALLS) {
        bulkhead.submit(scope) {
            highest.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            Thread.sleep(SLEEP_MILLIS)
            running.decrementAndGet()
            OK
        }
    }
    stages.forEach { stage -> stage.awaited() shouldBe OK }
    highest.get() shouldBeLessThanOrEqual PARALLEL
    bulkhead.snapshot().finished shouldBe CALLS.toLong()
    scope.cancel()
}

private fun submissions(pool: CoroutineDispatcher) {
    val scope = CoroutineScope(SupervisorJob())
    val bulkhead = dispatcherBulkhead("submissions", pool) {
        maxConcurrentCalls = 1
        queueCapacity = 1
    }
    val release = CountDownLatch(1)
    val blocking = bulkhead.submit(scope) { release.await() }
    val queued = bulkhead.submit(scope) { OK }
    val refused = bulkhead.submit(scope) { OK }
    val refusal = shouldThrow<ExecutionException> { refused.awaited() }
    refusal.cause.shouldBeInstanceOf<BulkheadFullException>()
    val pending = queued.toCompletableFuture()
    pending.cancel(true)
    release.countDown()
    blocking.awaited()
    val failing = bulkhead.submit(scope) { error("broken call") }
    shouldThrow<ExecutionException> { failing.awaited() }
    scope.isActive.shouldBeTrue()
    val snapshot = bulkhead.snapshot()
    snapshot.rejected shouldBe 1L
    snapshot.cancelled + snapshot.permitted shouldBe DECIDED
    scope.cancel()
    val late = bulkhead.submit(scope) { OK }
    shouldThrow<CancellationException> { late.awaited() }
    bulkhead.snapshot().queued shouldBe 0
}

private fun <T> CompletionStage<T>.awaited(): T {
    val future = toCompletableFuture()
    return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
}
