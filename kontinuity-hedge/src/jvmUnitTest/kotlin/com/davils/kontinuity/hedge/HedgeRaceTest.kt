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

import com.davils.kore.core.failure.attempted
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

private const val THREADS: Int = 8
private const val CALLS: Int = 2_000
private const val FAILING_EVERY: Int = 3
private const val SLOW_EVERY: Int = 5
private const val CANCELLED_EVERY: Int = 7
private const val PAUSE: Long = 2L

class HedgeRaceTest : FunSpec({
    test("every call is counted exactly once while copies race on real threads") {
        val hedge = hedge("race") {
            delay = fixedDelay(1.milliseconds)
            maxHedgedAttempts = 2
        }
        val entered = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(THREADS)
        pool.asCoroutineDispatcher().use { dispatcher ->
            runBlocking(dispatcher) {
                val calls = List(CALLS) { index ->
                    async {
                        entered.incrementAndGet()
                        attempted {
                            hedge.execute {
                                if (index % SLOW_EVERY == 0) delay(PAUSE.milliseconds)
                                check(index % FAILING_EVERY != 0) { "planned failure" }
                                index
                            }
                        }
                    }
                }
                calls.filterIndexed { index, _ -> index % CANCELLED_EVERY == 0 }.forEach { call -> call.cancel() }
                attempted { calls.awaitAll() }
            }
        }
        val snapshot = hedge.snapshot()
        snapshot.calls shouldBe entered.get().toLong()
        (entered.get() > CALLS / 2) shouldBe true
        (snapshot.hedgesStarted <= snapshot.calls * 2) shouldBe true
    }

    test("a fatal error of the platform passes through and counts as a failure") {
        val hedge = hedge("fatal")
        shouldThrow<StackOverflowError> { runBlocking { hedge.execute { throw StackOverflowError("planned") } } }
        hedge.snapshot().failed shouldBe 1L
        hedge.snapshot().calls shouldBe 1L
    }
})
