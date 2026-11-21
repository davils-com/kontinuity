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

import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val DEFAULT_CALLS: Int = 8
private const val DEFAULT_QUEUE: Int = 100
private const val CALLS: Int = 3
private const val QUEUE: Int = 5
private const val VIOLATIONS: Int = 2

class DispatcherBulkheadConfigTest : FunSpec({
    test("the default configuration runs 8 calls and queues 100") {
        val config = DispatcherBulkheadConfig.Default
        config.maxConcurrentCalls shouldBe DEFAULT_CALLS
        config.queueCapacity shouldBe DEFAULT_QUEUE
        config.toString() shouldBe "DispatcherBulkheadConfig(maxConcurrentCalls=8, queueCapacity=100)"
    }

    test("a configuration starts from its base") {
        val base = dispatcherBulkheadConfig { maxConcurrentCalls = CALLS }
        val derived = dispatcherBulkheadConfig(base) { queueCapacity = QUEUE }
        derived.maxConcurrentCalls shouldBe CALLS
        derived.queueCapacity shouldBe QUEUE
    }

    test("every value is allowed at its ceiling and refused past it") {
        val highest = dispatcherBulkheadConfig {
            maxConcurrentCalls = DispatcherBulkheadConfig.HARD_CONCURRENT_CALLS
            queueCapacity = DispatcherBulkheadConfig.HARD_QUEUE_CAPACITY
        }
        highest.maxConcurrentCalls shouldBe DispatcherBulkheadConfig.HARD_CONCURRENT_CALLS
        highest.queueCapacity shouldBe DispatcherBulkheadConfig.HARD_QUEUE_CAPACITY
        dispatcherBulkheadConfig { queueCapacity = 0 }.queueCapacity shouldBe 0
        val past = shouldThrow<ValidationException> {
            dispatcherBulkheadConfig {
                maxConcurrentCalls = DispatcherBulkheadConfig.HARD_CONCURRENT_CALLS + 1
                queueCapacity = DispatcherBulkheadConfig.HARD_QUEUE_CAPACITY + 1
            }
        }
        past.report.violations.size shouldBe VIOLATIONS
        val below = shouldThrow<ValidationException> {
            dispatcherBulkheadConfig {
                maxConcurrentCalls = 0
                queueCapacity = -1
            }
        }
        below.report.violations.size shouldBe VIOLATIONS
    }

    test("a builder cannot change after it was evaluated") {
        val builder = DispatcherBulkheadConfigBuilder()
        builder.build()
        shouldThrow<IllegalStateException> { builder.maxConcurrentCalls = CALLS }
        shouldThrow<IllegalStateException> { builder.queueCapacity = QUEUE }
    }
})
