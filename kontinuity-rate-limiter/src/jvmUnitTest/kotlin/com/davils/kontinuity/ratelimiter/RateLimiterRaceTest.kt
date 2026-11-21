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

package com.davils.kontinuity.ratelimiter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

private const val THREADS: Int = 8
private const val CALLS_PER_THREAD: Int = 5_000
private const val LIMIT: Int = 1_000
private const val ACCOUNTED_CALLS: Int = 400
private const val TIMEOUT_SECONDS: Long = 60L

class RateLimiterRaceTest : FunSpec({
    algorithms.forEach { (label, chosen) ->
        test("a $label never hands out more permits than its limit to racing threads") {
            val limiter = rateLimiter("race.limit") {
                limitForPeriod = LIMIT
                limitRefreshPeriod = 1.hours
                algorithm = chosen
            }
            val admitted = AtomicInteger(0)
            onThreads { repeat(CALLS_PER_THREAD) { if (limiter.tryAcquire()) admitted.incrementAndGet() } }
            admitted.get() shouldBe LIMIT
            limiter.snapshot().permitsGranted shouldBe LIMIT.toLong()
        }

        test("a $label stays consistent while threads drain it and change its limit") {
            val limiter = rateLimiter("race.changes") {
                limitForPeriod = LIMIT
                limitRefreshPeriod = 1.milliseconds
                algorithm = chosen
            }
            onThreads {
                repeat(CALLS_PER_THREAD) { index ->
                    limiter.drain()
                    limiter.changeLimitForPeriod(LIMIT - index % 2)
                    limiter.tryAcquire()
                }
            }
            (limiter.limitForPeriod in LIMIT - 1..LIMIT) shouldBe true
            limiter.snapshot().acquired shouldBe limiter.snapshot().permitsGranted
        }

        test("a $label counts every request of racing threads exactly once") {
            val limiter = rateLimiter("race.accounting") {
                limitForPeriod = LIMIT
                limitRefreshPeriod = 1.milliseconds
                timeout = 2.milliseconds
                maxWaitingCallers = THREADS / 2
                algorithm = chosen
            }
            onThreads {
                repeat(ACCOUNTED_CALLS) { index ->
                    runCatching { limiter.acquireBlocking(permits = index % LIMIT + 1) }
                    limiter.tryAcquire()
                }
            }
            val snapshot = limiter.snapshot()
            snapshot.acquired + snapshot.rejected + snapshot.cancelled shouldBe (THREADS * ACCOUNTED_CALLS * 2).toLong()
            snapshot.waitingCallers shouldBe 0
        }
    }
})

private val algorithms: List<Pair<String, RateLimiterAlgorithm>> = listOf(
    "fixed window" to RateLimiterAlgorithm.FixedWindow,
    "sliding window" to RateLimiterAlgorithm.SlidingWindow,
    "token bucket" to tokenBucket(LIMIT),
)

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
