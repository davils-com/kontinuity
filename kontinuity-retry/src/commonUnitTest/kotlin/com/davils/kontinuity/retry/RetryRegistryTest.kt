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

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.backoff.fixed
import com.davils.kontinuity.core.decorateFunction
import com.davils.kontinuity.core.invoke
import com.davils.kontinuity.retry.fixture.ANSWER
import com.davils.kontinuity.retry.fixture.BROKEN
import com.davils.kontinuity.retry.fixture.quickRetry
import com.davils.kontinuity.retry.fixture.virtualEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlin.time.Duration

private const val PATIENT: String = "patient"
private const val ORDERS: String = "orders"
private const val PATIENT_ATTEMPTS: Int = 7
private const val DEFAULT_ATTEMPTS: Int = 4
private const val SECOND: Int = 2

@OptIn(ExperimentalKotest::class)
class RetryRegistryTest : FunSpec({
    coroutineTestScope = true

    test("a registry creates every retry once from its configurations") {
        val environment = virtualEnvironment()
        val registry = retryRegistry(environment) {
            defaults { maxAttempts = DEFAULT_ATTEMPTS }
            config(PATIENT) { maxAttempts = PATIENT_ATTEMPTS }
        }
        val orders = registry.retry(ORDERS)
        orders.config.maxAttempts shouldBe DEFAULT_ATTEMPTS
        registry.retry(ORDERS) shouldBeSameInstanceAs orders
        registry.retry(name = "stock", config = PATIENT).config.maxAttempts shouldBe PATIENT_ATTEMPTS
        registry.retry("ledger", RetryConfig.Default).config shouldBe RetryConfig.Default
        val derived = registry.retry("invoices", base = PATIENT) { isFailingAfterMaxAttempts = true }
        derived.config.maxAttempts shouldBe PATIENT_ATTEMPTS
        derived.config.isFailingAfterMaxAttempts shouldBe true
        registry.retry(ORDERS) { maxAttempts = 1 } shouldBeSameInstanceAs orders
        shouldThrow<IllegalArgumentException> { registry.retry("unknown", base = "missing") { maxAttempts = 1 } }
        shouldThrow<IllegalArgumentException> { registry.retry(name = "unknown", config = "missing") }
        registry.environment shouldBe environment
        registry.toString() shouldBe "RetryRegistry(4 retries)"
    }

    test("a retry is built from a configuration or with the defaults") {
        val config = retryConfig { maxAttempts = 1 }
        retry(ORDERS, config).config shouldBe config
        retry(ORDERS).config.toString() shouldBe RetryConfig.Default.toString()
        retry(ORDERS, config, Environment.Default).toString() shouldBe "Retry(orders, maxAttempts=1)"
        retryRegistry().all.size shouldBe 0
        shouldThrow<IllegalArgumentException> { retry("bad name!") }
    }

    test("a flow is collected again from its start, so its elements are emitted again") {
        val retry = quickRetry(virtualEnvironment())
        var collections = 0
        val upstream = flow {
            collections++
            emit(1)
            check(collections > 1) { BROKEN }
            emit(SECOND)
        }
        upstream.retry(retry).toList() shouldBe listOf(1, 1, SECOND)
        collections shouldBe SECOND
    }

    test("a retry decorates functions and is invoked as an operator") {
        val retry = quickRetry(virtualEnvironment()) { backoff = fixed(Duration.ZERO) }
        var calls = 0
        val decorated = retry.decorateFunction { value: Int ->
            calls++
            check(calls > 1) { BROKEN }
            value
        }
        decorated(ANSWER) shouldBe ANSWER
        retry { ANSWER } shouldBe ANSWER
    }
})
