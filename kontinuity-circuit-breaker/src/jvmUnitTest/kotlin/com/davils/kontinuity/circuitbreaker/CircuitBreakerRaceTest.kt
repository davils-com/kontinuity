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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val THREADS: Int = 8
private const val CALLS_PER_THREAD: Int = 2_000
private const val TRIALS: Int = 5
private const val WINDOW: Int = 50
private const val FAILING_EVERY: Int = 3
private const val TIMEOUT_SECONDS: Long = 60L

class CircuitBreakerRaceTest : FunSpec({
    test("a half-open breaker never admits more trial calls than it allows") {
        val breaker = circuitBreaker("race.trials") { permittedCallsInHalfOpenState = TRIALS }
        breaker.transitionTo(CircuitState.HalfOpen)
        val admitted = AtomicInteger(0)
        onThreads {
            repeat(
                CALLS_PER_THREAD,
            ) { if (breaker.tryAcquirePermission() != null) admitted.incrementAndGet() }
        }
        admitted.get() shouldBe TRIALS
    }

    test("every call is counted exactly once while the breaker opens and closes") {
        val breaker = circuitBreaker("race.accounting") {
            slidingWindow = countBased(WINDOW)
            minimumNumberOfCalls = WINDOW
            permittedCallsInHalfOpenState = TRIALS
        }
        val counter = AtomicInteger(0)
        onThreads {
            repeat(CALLS_PER_THREAD) { index ->
                runCatching { breaker.executeBlocking { check(index % FAILING_EVERY != 0) { "planned failure" } } }
                if (counter.incrementAndGet() % WINDOW == 0) breaker.transitionTo(CircuitState.HalfOpen)
            }
        }
        val totals = breaker.snapshot().totals
        val counted = totals.successful + totals.failed + totals.ignored + totals.released + totals.notPermitted
        counted shouldBe (THREADS * CALLS_PER_THREAD).toLong()
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
