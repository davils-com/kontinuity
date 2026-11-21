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

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.policy.fixture.ANSWER
import com.davils.kontinuity.policy.fixture.quietly
import com.davils.kontinuity.ratelimiter.rateLimiter
import com.davils.kontinuity.testing.CountingStage
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.seconds

private const val ORDERS: String = "orders"

@OptIn(ExperimentalKotest::class)
class PolicyEdgeTest : FunSpec({
    coroutineTestScope = true

    test("a policy can be built with the default environment") {
        val policy = resilience(ORDERS) { stage(CountingStage()) }
        policy.execute { ANSWER } shouldBe ANSWER
        Environment.Default.meters shouldBe null
    }

    test("a policy takes at least one permit") {
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        shouldThrow<ValidationException> {
            resilience(ORDERS, environment) { rateLimiter(rateLimiter(ORDERS, environment), permits = 0) }
        }
    }

    test("a failure of an anonymous type is reported without a name") {
        val policy = resilience(ORDERS, testEnvironment(testCoroutineScheduler.timeSource)) { stage(CountingStage()) }
        val log = recordEvents(policy.events)
        quietly { policy.execute<Int> { throw object : RuntimeException("anonymous") {} } }
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.events.map(PolicyEvent::toString) shouldContain "Failed(orders, 0s, Throwable)"
    }

    test("a cancelled call is counted without meters") {
        val policy = resilience(ORDERS, testEnvironment(testCoroutineScheduler.timeSource)) { stage(CountingStage()) }
        val call = async { policy.execute { delay(1.seconds) } }
        yield()
        call.cancel()
        shouldThrow<CancellationException> { call.await() }
        policy.snapshot().cancelled shouldBe 1L
    }
})
