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

import com.davils.kollect.core.meterRegistry
import com.davils.kontinuity.core.environment
import com.davils.kore.core.failure.attempted
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

private const val THREADS: Int = 8
private const val CALLS_PER_THREAD: Int = 500
private const val LIMIT: Int = 3
private const val COROUTINES: Int = 2_000
private const val CANCELLED_EVERY: Int = 3
private const val TIMEOUT_SECONDS: Long = 60L
private val WAIT = 20.milliseconds
private val PATIENCE = 2.milliseconds

class BulkheadRaceTest : FunSpec({
    test("real threads never run more calls at once than the bulkhead permits") {
        val registry = meterRegistry()
        val bulkhead = bulkhead("race.threads", environment { meters(registry) }) {
            maxConcurrentCalls = LIMIT
            maxWaitDuration = WAIT
            maxWaitingCalls = THREADS
        }
        val running = AtomicInteger(0)
        val highest = AtomicInteger(0)
        onThreads {
            repeat(CALLS_PER_THREAD) {
                runCatching {
                    bulkhead.executeBlocking {
                        highest.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                        Thread.onSpinWait()
                        running.decrementAndGet()
                    }
                }
            }
        }
        highest.get() shouldBeLessThanOrEqual LIMIT
        val snapshot = bulkhead.snapshot()
        snapshot.permitted + snapshot.rejected + snapshot.cancelled shouldBe (THREADS * CALLS_PER_THREAD).toLong()
        snapshot.permitted shouldBe snapshot.finished
        snapshot.availableConcurrentCalls shouldBe LIMIT
        snapshot.waitingCalls shouldBe 0
    }

    test("coroutines on many threads lose no permit under timeouts and cancellations") {
        val dispatcher = Executors.newFixedThreadPool(THREADS).asCoroutineDispatcher()
        val bulkhead = bulkhead("race.coroutines", environment { meters(meterRegistry()) }) {
            maxConcurrentCalls = LIMIT
            maxWaitDuration = WAIT
            maxWaitingCalls = COROUTINES
        }
        val running = AtomicInteger(0)
        val highest = AtomicInteger(0)
        dispatcher.use { pool -> runBlocking(pool) { race(bulkhead, running, highest) } }
        highest.get() shouldBeLessThanOrEqual LIMIT
        val snapshot = bulkhead.snapshot()
        snapshot.permitted + snapshot.rejected + snapshot.cancelled shouldBe COROUTINES.toLong()
        snapshot.permitted shouldBe snapshot.finished
        snapshot.availableConcurrentCalls shouldBe LIMIT
        snapshot.waitingCalls shouldBe 0
    }

    test("a permit released by many threads at once is given back once") {
        val bulkhead = bulkhead("race.release") { maxConcurrentCalls = 2 }
        val permit = bulkhead.tryAcquirePermission() ?: error("a permit was free")
        onThreads { permit.release() }
        bulkhead.snapshot().finished shouldBe 1L
        bulkhead.snapshot().availableConcurrentCalls shouldBe 2
    }
})

private suspend fun race(bulkhead: Bulkhead, running: AtomicInteger, highest: AtomicInteger) {
    coroutineScope {
        repeat(COROUTINES) { index ->
            launch {
                withTimeoutOrNull(PATIENCE * (index % CANCELLED_EVERY + 1)) {
                    attempted { bulkhead.execute { holdCounted(running, highest) } }
                }
            }
        }
    }
}

private suspend fun holdCounted(running: AtomicInteger, highest: AtomicInteger) {
    highest.accumulateAndGet(running.incrementAndGet(), ::maxOf)
    try {
        delay(1.milliseconds)
    } finally {
        running.decrementAndGet()
    }
}

private fun onThreads(work: () -> Unit) {
    val pool = Executors.newFixedThreadPool(THREADS)
    val start = CountDownLatch(1)
    repeat(THREADS) {
        pool.execute {
            start.await()
            work()
        }
    }
    start.countDown()
    pool.shutdown()
    pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
}
