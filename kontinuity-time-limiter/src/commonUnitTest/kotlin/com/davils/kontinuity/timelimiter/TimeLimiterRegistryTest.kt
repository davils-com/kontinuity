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

import com.davils.kontinuity.core.CallTimeoutException
import com.davils.kontinuity.core.registry.InstanceLimitException
import com.davils.kontinuity.testing.testEnvironment
import com.davils.kontinuity.timelimiter.fixture.TIMEOUT
import com.davils.kontinuity.timelimiter.fixture.hardLimiter
import com.davils.kontinuity.timelimiter.fixture.softLimiter
import com.davils.kontinuity.timelimiter.fixture.withScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlin.time.Duration.Companion.seconds

private const val PAYMENTS: String = "payments"
private const val ORDERS: String = "orders"
private const val SLOW: String = "slow"
private const val ELEMENTS: Int = 3

@OptIn(ExperimentalKotest::class)
class TimeLimiterRegistryTest : FunSpec({
    coroutineTestScope = true

    test("a registry creates limiters from its configurations once per name") {
        val registry = timeLimiterRegistry {
            defaults { timeout = 2.seconds }
            config(SLOW) { timeout = 10.seconds }
        }
        val payments = registry.timeLimiter(PAYMENTS)
        payments.config.timeout shouldBe 2.seconds
        registry.timeLimiter(name = PAYMENTS, config = SLOW) shouldBeSameInstanceAs payments
        registry.timeLimiter(name = ORDERS, config = SLOW).config.timeout shouldBe 10.seconds
        registry.all.map { limiter -> limiter.name.value } shouldBe listOf(PAYMENTS, ORDERS)
        registry.toString() shouldBe "TimeLimiterRegistry(2 time limiters)"
    }

    test("a registry creates a limiter from a configuration or a block") {
        val registry = timeLimiterRegistry { config(SLOW) { timeout = 10.seconds } }
        val custom = registry.timeLimiter(PAYMENTS, base = SLOW) { strategy = TimeoutStrategy.Hard }
        custom.config.timeout shouldBe 10.seconds
        registry.timeLimiter(PAYMENTS) { timeout = 2.seconds } shouldBeSameInstanceAs custom
        val given = registry.timeLimiter(ORDERS, timeLimiterConfig { timeout = 2.seconds })
        given.config.timeout shouldBe 2.seconds
        shouldThrow<IllegalArgumentException> { registry.timeLimiter("other", base = "missing") {} }
    }

    test("a full registry refuses new limiters") {
        val registry = timeLimiterRegistry { limits { maximumInstances = 1 } }
        registry.timeLimiter(PAYMENTS)
        shouldThrow<InstanceLimitException> { registry.timeLimiter(ORDERS) }
    }

    test("a registry without a scope refuses soft limiters") {
        val registry = timeLimiterRegistry { defaults { strategy = TimeoutStrategy.Soft() } }
        shouldThrow<IllegalArgumentException> { registry.timeLimiter(PAYMENTS) }
        registry.all shouldBe emptyList()
    }

    test("a limiter from a configuration built before keeps it") {
        val config = timeLimiterConfig { timeout = 2.seconds }
        timeLimiter(PAYMENTS, config).config shouldBeSameInstanceAs config
        timeLimiterRegistry().environment.scope shouldBe null
        timeLimiter(ORDERS).config.timeout shouldBe TimeLimiterConfig.Default.timeout
    }

    test("a flow collected through a limiter is timed as a whole") {
        val limiter = hardLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        val elements = flow {
            repeat(ELEMENTS) { index ->
                delay(TIMEOUT / ELEMENTS / 2)
                emit(index)
            }
        }
        elements.timeLimiter(limiter).toList() shouldBe listOf(0, 1, 2)
        val endless = flow {
            repeat(ELEMENTS) { index ->
                delay(TIMEOUT)
                emit(index)
            }
        }
        shouldThrow<CallTimeoutException> { endless.timeLimiter(limiter).toList() }
        limiter.snapshot().successful shouldBe 1L
        limiter.snapshot().timedOut shouldBe 1L
    }

    test("a flow collected through a soft limiter runs on the scope") {
        withScope { scope ->
            val limiter = softLimiter(testEnvironment(testCoroutineScheduler.timeSource, scope))
            val elements = flow { repeat(ELEMENTS) { index -> emit(index) } }
            elements.timeLimiter(limiter).toList() shouldBe listOf(0, 1, 2)
        }
    }
})
