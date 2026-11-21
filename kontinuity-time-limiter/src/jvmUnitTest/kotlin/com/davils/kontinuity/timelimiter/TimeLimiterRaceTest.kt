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

import com.davils.kontinuity.core.environment
import com.davils.kore.core.failure.attempted
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

private const val THREADS: Int = 8
private const val CALLS_PER_THREAD: Int = 100
private const val MAX_DETACHED: Int = 4
private const val KINDS: Int = 4
private const val TIMEOUT_SECONDS: Long = 60L
private const val POLL_MILLIS: Long = 5L
private val TIMEOUT = 2.milliseconds

class TimeLimiterRaceTest : FunSpec({
    test("every call is counted exactly once and the detached calls never pass their limit") {
        val most = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(THREADS)
        val limiter = pool.asCoroutineDispatcher().use { dispatcher ->
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            val limiter = timeLimiter("race", environment { scope(scope) }) {
                timeout = TIMEOUT
                strategy = TimeoutStrategy.Soft(isCancellingOnTimeout = false, maxDetached = MAX_DETACHED)
            }
            onThreads {
                runBlocking {
                    repeat(CALLS_PER_THREAD) { index ->
                        attempted {
                            limiter.execute {
                                most.accumulateAndGet(limiter.snapshot().detachedInFlight, ::maxOf)
                                check(index % KINDS != 0) { "planned failure" }
                                delay(TIMEOUT * (index % KINDS))
                            }
                        }
                    }
                }
            }
            while (limiter.snapshot().detachedInFlight > 0) delay(POLL_MILLIS)
            scope.cancel()
            limiter
        }
        val snapshot = limiter.snapshot()
        val counted = snapshot.successful +
            snapshot.failed +
            snapshot.timedOut +
            snapshot.cancelled +
            snapshot.rejected
        counted shouldBe (THREADS * CALLS_PER_THREAD).toLong()
        (most.get() <= MAX_DETACHED) shouldBe true
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
