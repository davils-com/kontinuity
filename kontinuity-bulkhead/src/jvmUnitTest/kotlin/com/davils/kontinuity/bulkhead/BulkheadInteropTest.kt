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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val OK: Int = 200
private const val RELEASED: Long = 3L
private const val BROKEN: String = "broken"
private const val WAITING_POLL_MILLIS: Long = 5L
private val SHORT = 50.milliseconds
private val PATIENT = 10.seconds

class BulkheadInteropTest : FunSpec({
    test("a blocking call runs while it holds a permit") {
        val bulkhead = bulkhead("blocking") { maxConcurrentCalls = 1 }
        bulkhead.executeBlocking { OK } shouldBe OK
        shouldThrow<IllegalStateException> { bulkhead.executeBlocking { error(BROKEN) } }
        val holder = bulkhead.acquirePermissionNow()
        shouldThrow<BulkheadFullException> { bulkhead.executeBlocking { OK } }.isQueueFull shouldBe false
        holder.release()
        bulkhead.snapshot().finished shouldBe RELEASED
    }

    test("a blocking call parks until a permit is released") {
        val bulkhead = bulkhead("parking") {
            maxConcurrentCalls = 1
            maxWaitDuration = PATIENT
        }
        val holder = bulkhead.acquirePermissionNow()
        thread {
            awaitWaiting(bulkhead)
            holder.release()
        }
        bulkhead.executeBlocking { OK } shouldBe OK
        bulkhead.snapshot().permitted shouldBe 2L
    }

    test("a blocking call that waits too long is refused") {
        val bulkhead = bulkhead("impatient") {
            maxConcurrentCalls = 1
            maxWaitDuration = SHORT
        }
        val holder = bulkhead.acquirePermissionNow()
        shouldThrow<BulkheadFullException> { bulkhead.executeBlocking { OK } }.isQueueFull shouldBe false
        bulkhead.snapshot().waitingCalls shouldBe 0
        holder.release()
        bulkhead.snapshot().availableConcurrentCalls shouldBe 1
    }

    test("an interrupted blocking wait leaves the queue and is counted as cancelled") {
        val bulkhead = bulkhead("interrupted") {
            maxConcurrentCalls = 1
            maxWaitDuration = PATIENT
        }
        val holder = bulkhead.acquirePermissionNow()
        val failure = AtomicReference<Throwable>()
        val waiter = thread { failure.set(runCatching { bulkhead.executeBlocking { OK } }.exceptionOrNull()) }
        awaitWaiting(bulkhead)
        waiter.interrupt()
        waiter.join()
        failure.get().shouldBeInstanceOf<InterruptedException>()
        bulkhead.snapshot().cancelled shouldBe 1L
        bulkhead.snapshot().waitingCalls shouldBe 0
        holder.release()
        bulkhead.snapshot().availableConcurrentCalls shouldBe 1
    }

    test("a completion stage holds its permit until it completes") {
        val bulkhead = bulkhead("stages") { maxConcurrentCalls = 1 }
        val pending = CompletableFuture<Int>()
        val stage = bulkhead.executeCompletionStage { pending }
        bulkhead.snapshot().concurrentCalls shouldBe 1
        val refused = bulkhead.executeCompletionStage { CompletableFuture.completedFuture(OK) }
        shouldThrow<ExecutionException> { refused.toCompletableFuture().get() }
            .cause
            .shouldBeInstanceOf<BulkheadFullException>()
        pending.complete(OK)
        stage.toCompletableFuture().get() shouldBe OK
        val throwing = bulkhead.executeCompletionStage<Int> { throw IllegalStateException(BROKEN) }
        shouldThrow<ExecutionException> { throwing.toCompletableFuture().get() }
        bulkhead.snapshot().finished shouldBe 2L
        bulkhead.snapshot().availableConcurrentCalls shouldBe 1
    }
})

private fun Bulkhead.acquirePermissionNow(): BulkheadPermit = tryAcquirePermission() ?: error("a permit was free")

private fun awaitWaiting(bulkhead: Bulkhead) {
    while (bulkhead.snapshot().waitingCalls == 0) Thread.sleep(WAITING_POLL_MILLIS)
}
