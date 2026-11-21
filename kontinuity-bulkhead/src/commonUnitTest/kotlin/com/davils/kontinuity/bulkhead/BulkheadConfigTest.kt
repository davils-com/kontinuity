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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_CALLS: Int = 25
private const val DEFAULT_WAITING: Int = 1_000
private const val CALLS: Int = 4
private const val WAITING: Int = 7
private const val VIOLATIONS: Int = 3

class BulkheadConfigTest : FunSpec({
    test("the default configuration has the defaults of resilience4j") {
        val config = BulkheadConfig.Default
        config.maxConcurrentCalls shouldBe DEFAULT_CALLS
        config.maxWaitDuration shouldBe Duration.ZERO
        config.maxWaitingCalls shouldBe DEFAULT_WAITING
        config.toString() shouldBe "BulkheadConfig(maxConcurrentCalls=25, maxWaitDuration=0s, maxWaitingCalls=1000)"
    }

    test("a configuration starts from its base") {
        val base = bulkheadConfig { maxConcurrentCalls = CALLS }
        val derived = bulkheadConfig(base) { maxWaitingCalls = WAITING }
        derived.maxConcurrentCalls shouldBe CALLS
        derived.maxWaitingCalls shouldBe WAITING
        derived.maxWaitDuration shouldBe Duration.ZERO
    }

    test("every value is allowed at its ceiling") {
        val config = bulkheadConfig {
            maxConcurrentCalls = BulkheadConfig.HARD_CONCURRENT_CALLS
            maxWaitDuration = BulkheadConfig.HARD_WAIT
            maxWaitingCalls = BulkheadConfig.HARD_WAITING_CALLS
        }
        config.maxConcurrentCalls shouldBe BulkheadConfig.HARD_CONCURRENT_CALLS
        config.maxWaitDuration shouldBe BulkheadConfig.HARD_WAIT
        config.maxWaitingCalls shouldBe BulkheadConfig.HARD_WAITING_CALLS
        bulkheadConfig { maxWaitingCalls = 0 }.maxWaitingCalls shouldBe 0
    }

    test("every value past its ceiling is refused") {
        val past = shouldThrow<ValidationException> {
            bulkheadConfig {
                maxConcurrentCalls = BulkheadConfig.HARD_CONCURRENT_CALLS + 1
                maxWaitDuration = BulkheadConfig.HARD_WAIT + 1.nanoseconds
                maxWaitingCalls = BulkheadConfig.HARD_WAITING_CALLS + 1
            }
        }
        past.report.violations.size shouldBe VIOLATIONS
        val below = shouldThrow<ValidationException> {
            bulkheadConfig {
                maxConcurrentCalls = 0
                maxWaitDuration = (-1).seconds
                maxWaitingCalls = -1
            }
        }
        below.report.violations.size shouldBe VIOLATIONS
    }

    test("a builder cannot change after it was evaluated") {
        val builder = BulkheadConfigBuilder()
        builder.build()
        shouldThrow<IllegalStateException> { builder.maxConcurrentCalls = CALLS }
        shouldThrow<IllegalStateException> { builder.maxWaitDuration = 1.seconds }
        shouldThrow<IllegalStateException> { builder.maxWaitingCalls = WAITING }
    }
})
