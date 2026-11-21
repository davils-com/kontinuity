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

package com.davils.kontinuity.bulkhead

import com.davils.kontinuity.core.registry.InstanceLimitException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

private const val PAYMENTS: String = "payments"
private const val ORDERS: String = "orders"
private const val NARROW: String = "narrow"
private const val NARROW_CALLS: Int = 2
private const val DEFAULT_CALLS: Int = 10
private const val CUSTOM_CALLS: Int = 5

class BulkheadRegistryTest : FunSpec({
    test("a registry creates bulkheads from its configurations once per name") {
        val registry = bulkheadRegistry {
            defaults { maxConcurrentCalls = DEFAULT_CALLS }
            config(NARROW) { maxConcurrentCalls = NARROW_CALLS }
        }
        val payments = registry.bulkhead(PAYMENTS)
        payments.config.maxConcurrentCalls shouldBe DEFAULT_CALLS
        registry.bulkhead(name = PAYMENTS, config = NARROW) shouldBeSameInstanceAs payments
        registry.bulkhead(name = ORDERS, config = NARROW).config.maxConcurrentCalls shouldBe NARROW_CALLS
        registry.all.map { bulkhead -> bulkhead.name.value } shouldBe listOf(PAYMENTS, ORDERS)
        registry.toString() shouldBe "BulkheadRegistry(2 bulkheads)"
    }

    test("a registry creates a bulkhead from a configuration or a block") {
        val registry = bulkheadRegistry { config(NARROW) { maxConcurrentCalls = NARROW_CALLS } }
        val custom = registry.bulkhead(PAYMENTS, base = NARROW) { maxWaitingCalls = 1 }
        custom.config.maxConcurrentCalls shouldBe NARROW_CALLS
        custom.config.maxWaitingCalls shouldBe 1
        registry.bulkhead(PAYMENTS) { maxConcurrentCalls = CUSTOM_CALLS } shouldBeSameInstanceAs custom
        val given = registry.bulkhead(ORDERS, bulkheadConfig { maxConcurrentCalls = CUSTOM_CALLS })
        given.config.maxConcurrentCalls shouldBe CUSTOM_CALLS
        shouldThrow<IllegalArgumentException> { registry.bulkhead("other", base = "missing") {} }
    }

    test("a full registry refuses new bulkheads") {
        val registry = bulkheadRegistry { limits { maximumInstances = 1 } }
        registry.bulkhead(PAYMENTS)
        shouldThrow<InstanceLimitException> { registry.bulkhead(ORDERS) }
        registry.environment.scope shouldBe null
    }

    test("a bulkhead from a configuration built before keeps it") {
        val config = bulkheadConfig { maxConcurrentCalls = CUSTOM_CALLS }
        bulkhead(PAYMENTS, config).config shouldBeSameInstanceAs config
    }
})
