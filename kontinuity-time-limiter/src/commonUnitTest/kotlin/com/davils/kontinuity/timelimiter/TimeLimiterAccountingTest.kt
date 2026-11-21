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

import com.davils.kontinuity.testing.testEnvironment
import com.davils.kontinuity.timelimiter.fixture.LONG
import com.davils.kontinuity.timelimiter.fixture.SHORT
import com.davils.kontinuity.timelimiter.fixture.TIMEOUT
import com.davils.kontinuity.timelimiter.fixture.quietly
import com.davils.kontinuity.timelimiter.fixture.softLimiter
import com.davils.kontinuity.timelimiter.fixture.withScope
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val CALLS: Int = 60
private const val DETACHED: Int = 3
private const val CANCELLED: Int = 3

@OptIn(ExperimentalKotest::class)
class TimeLimiterAccountingTest : FunSpec({
    coroutineTestScope = true

    test("every call is counted exactly once") {
        withScope { scope ->
            val limiter = softLimiter(
                testEnvironment(testCoroutineScheduler.timeSource, scope),
                isCancellingOnTimeout = false,
                maxDetached = DETACHED,
            )
            val callers = mutableListOf<Job>()
            repeat(CALLS) { index ->
                val caller = launch { quietly { limiter.execute { behaviours[index % behaviours.size]() } } }
                callers += caller
                if (index % behaviours.size == CANCELLED) {
                    delay(SHORT)
                    caller.cancel()
                }
                delay(TIMEOUT / behaviours.size)
            }
            callers.forEach { caller -> caller.join() }
            delay(LONG)
            val snapshot = limiter.snapshot()
            val counted = snapshot.successful +
                snapshot.failed +
                snapshot.timedOut +
                snapshot.cancelled +
                snapshot.rejected
            counted shouldBe CALLS.toLong()
            snapshot.detachedInFlight shouldBe 0
            (snapshot.rejected > 0L) shouldBe true
        }
    }
})

private val behaviours: List<suspend () -> Unit> = listOf(
    { delay(SHORT / 2) },
    { error("planned failure") },
    { delay(LONG) },
    { delay(LONG) },
    { withTimeout(SHORT / 2) { delay(LONG) } },
    { delay(TIMEOUT * 2) },
)
