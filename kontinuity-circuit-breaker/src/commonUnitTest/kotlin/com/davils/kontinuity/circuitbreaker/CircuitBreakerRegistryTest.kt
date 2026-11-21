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

import com.davils.kollect.core.health.HealthStatus
import com.davils.kontinuity.core.registry.InstanceLimitException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

private const val PAYMENTS: String = "payments"
private const val ORDERS: String = "orders"
private const val STRICT: String = "strict"
private const val STRICT_RATE: Double = 10.0
private const val DEFAULT_RATE: Double = 40.0
private const val CUSTOM_RATE: Double = 20.0

@OptIn(ExperimentalKotest::class)
class CircuitBreakerRegistryTest : FunSpec({
    coroutineTestScope = true

    test("a registry creates breakers from its configurations once per name") {
        val registry = circuitBreakerRegistry {
            defaults { failureRateThreshold = DEFAULT_RATE }
            config(STRICT) { failureRateThreshold = STRICT_RATE }
        }
        val payments = registry.circuitBreaker(PAYMENTS)
        payments.config.failureRateThreshold shouldBe DEFAULT_RATE
        registry.circuitBreaker(name = PAYMENTS, config = STRICT) shouldBeSameInstanceAs payments
        registry.circuitBreaker(name = ORDERS, config = STRICT).config.failureRateThreshold shouldBe STRICT_RATE
        registry.all.map { breaker -> breaker.name.value } shouldBe listOf(PAYMENTS, ORDERS)
        registry.toString() shouldBe "CircuitBreakerRegistry(2 circuit breakers)"
    }

    test("a registry creates a breaker from a configuration or a block") {
        val registry = circuitBreakerRegistry { config(STRICT) { failureRateThreshold = STRICT_RATE } }
        val custom = registry.circuitBreaker(PAYMENTS, base = STRICT) { minimumNumberOfCalls = 1 }
        custom.config.failureRateThreshold shouldBe STRICT_RATE
        custom.config.minimumNumberOfCalls shouldBe 1
        registry.circuitBreaker(PAYMENTS) { failureRateThreshold = CUSTOM_RATE } shouldBeSameInstanceAs custom
        val given = registry.circuitBreaker(ORDERS, circuitBreakerConfig { failureRateThreshold = CUSTOM_RATE })
        given.config.failureRateThreshold shouldBe CUSTOM_RATE
        shouldThrow<IllegalArgumentException> { registry.circuitBreaker("other", base = "missing") {} }
    }

    test("a full registry refuses new breakers") {
        val registry = circuitBreakerRegistry { limits { maximumInstances = 1 } }
        registry.circuitBreaker(PAYMENTS)
        shouldThrow<InstanceLimitException> { registry.circuitBreaker(ORDERS) }
    }

    test("the health of a registry is the worst health of its breakers") {
        val registry = circuitBreakerRegistry()
        registry.healthIndicator().check().status shouldBe HealthStatus.Up
        val payments = registry.circuitBreaker(PAYMENTS)
        registry.circuitBreaker(ORDERS).transitionTo(CircuitState.HalfOpen)
        registry.healthIndicator().check().status shouldBe HealthStatus.Degraded
        payments.transitionTo(CircuitState.Open)
        registry.healthIndicator().check().status shouldBe HealthStatus.Down
    }

    test("a breaker from a configuration built before keeps it") {
        val config = circuitBreakerConfig { failureRateThreshold = CUSTOM_RATE }
        circuitBreaker(PAYMENTS, config).config shouldBeSameInstanceAs config
        registry().environment.scope shouldBe null
    }
})

private fun registry(): CircuitBreakerRegistry = circuitBreakerRegistry()
