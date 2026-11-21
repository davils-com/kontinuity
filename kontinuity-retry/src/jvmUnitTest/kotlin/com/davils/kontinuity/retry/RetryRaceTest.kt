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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val THREADS: Int = 8
private const val CALLS_PER_THREAD: Int = 2_000
private const val RESERVE: Int = 100
private const val FAILING_EVERY: Int = 3
private const val TIMEOUT_SECONDS: Long = 60L

class RetryRaceTest : FunSpec({
    test("a shared budget never grants more retries than its reserve") {
        val budget = retryBudget {
            ratio = 0.0
            minRetriesPerSecond = 1.0
            window = RESERVE.seconds
        }
        val granted = AtomicInteger(0)
        onThreads { repeat(CALLS_PER_THREAD) { if (budget.tryWithdraw()) granted.incrementAndGet() } }
        granted.get() shouldBe RESERVE
        budget.balance shouldBe 0L
    }

    test("every call is counted exactly once while threads share a budget") {
        val budget = retryBudget {
            ratio = 0.0
            minRetriesPerSecond = 1.0
            window = RESERVE.seconds
        }
        val retry = retry("race") {
            backoff = fixed(Duration.ZERO)
            this.budget = budget
        }
        onThreads {
            repeat(CALLS_PER_THREAD) { index ->
                runCatching { retry.executeBlocking { check(index % FAILING_EVERY != 0) { "planned failure" } } }
            }
        }
        val snapshot = retry.snapshot()
        snapshot.calls shouldBe (THREADS * CALLS_PER_THREAD).toLong()
        snapshot.retries shouldBeLessThanOrEqualTo RESERVE.toLong()
    }
})

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
