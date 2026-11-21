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

package com.davils.kontinuity.policy

import com.davils.kontinuity.bulkhead.bulkhead
import com.davils.kontinuity.circuitbreaker.circuitBreaker
import com.davils.kontinuity.circuitbreaker.countBased
import com.davils.kontinuity.core.backoff.fixed
import com.davils.kontinuity.retry.retry
import com.davils.kore.core.failure.attempted
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.time.Duration

private const val THREADS: Int = 8
private const val CALLS: Int = 4_000
private const val FAILING_EVERY: Int = 3
private const val SEATS: Int = 4
private const val WINDOW: Int = 20

class PolicyRaceTest : FunSpec({
    test("a policy counts every call exactly once under real threads") {
        val breaker = circuitBreaker("race.breaker") {
            slidingWindow = countBased(WINDOW)
            minimumNumberOfCalls = WINDOW
        }
        val policy = resilience("race") {
            retry(retry("race.retry") { backoff = fixed(Duration.ZERO) })
            circuitBreaker(breaker)
            bulkhead(bulkhead("race.seats") { maxConcurrentCalls = SEATS })
        }
        val pool = Executors.newFixedThreadPool(THREADS)
        pool.asCoroutineDispatcher().use { dispatcher ->
            withContext(dispatcher) {
                val calls = List(CALLS) { index ->
                    async { attempted { policy.execute { check(index % FAILING_EVERY != 0) { "planned failure" } } } }
                }
                calls.awaitAll()
            }
        }
        policy.snapshot().calls shouldBe CALLS.toLong()
    }
})
