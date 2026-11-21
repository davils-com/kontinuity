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

import com.davils.kontinuity.core.backoff.Backoff
import com.davils.kontinuity.core.backoff.fixed
import com.davils.kontinuity.core.backoff.jittered
import com.davils.kontinuity.core.backoff.proportional
import com.davils.kontinuity.core.filter.FailureFilter
import com.davils.kontinuity.core.filter.ResultFilter
import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val ATTEMPTS: Int = 5
private const val DEFAULT_ATTEMPTS: Int = 3
private const val DEFAULT_WAIT: Int = 500
private const val VIOLATIONS: Int = 2
private const val HALF: Double = 0.5

class RetryConfigTest : FunSpec({
    test("the default configuration has the defaults of resilience4j") {
        val config = RetryConfig.Default
        config.maxAttempts shouldBe DEFAULT_ATTEMPTS
        config.backoff shouldBe fixed(DEFAULT_WAIT.milliseconds)
        config.failures shouldBe FailureFilter.RecordAll
        config.results shouldBe ResultFilter.None
        config.interval shouldBe null
        config.isFailingAfterMaxAttempts shouldBe false
        config.onDiscardedResult shouldBe null
        config.budget shouldBe null
    }

    test("a configuration starts from its base") {
        val budget = retryBudget()
        val base = retryConfig {
            maxAttempts = ATTEMPTS
            this.budget = budget
            interval { _, _, _ -> null }
        }
        val derived = retryConfig(base) { isFailingAfterMaxAttempts = true }
        derived.maxAttempts shouldBe ATTEMPTS
        derived.budget shouldBe budget
        derived.isFailingAfterMaxAttempts shouldBe true
        derived.toString() shouldBe "RetryConfig(maxAttempts=5, backoff=Fixed(500ms), " +
            "failingAfterMaxAttempts=true, interval=true, budget=true)"
        RetryConfig.Default.toString() shouldBe "RetryConfig(maxAttempts=3, backoff=Fixed(500ms), " +
            "failingAfterMaxAttempts=false, interval=false, budget=false)"
    }

    test("the attempts are bounded by their hard ceiling") {
        retryConfig { maxAttempts = RetryConfig.HARD_ATTEMPTS }.maxAttempts shouldBe RetryConfig.HARD_ATTEMPTS
        retryConfig { maxAttempts = 1 }.maxAttempts shouldBe 1
        shouldThrow<ValidationException> { retryConfig { maxAttempts = RetryConfig.HARD_ATTEMPTS + 1 } }
        shouldThrow<ValidationException> { retryConfig { maxAttempts = 0 } }
    }

    test("a backoff whose maximum exceeds the hard ceiling is refused, every problem at once") {
        val longest = fixed(Backoff.HARD_MAXIMUM_DELAY)
        retryConfig { backoff = longest }.backoff shouldBe longest
        val refusal = shouldThrow<ValidationException> {
            retryConfig {
                maxAttempts = 0
                backoff = longest.jittered(proportional(HALF))
            }
        }
        refusal.report.violations.size shouldBe VIOLATIONS
    }

    test("a builder cannot change after it was evaluated") {
        val builder = RetryConfigBuilder()
        builder.build()
        shouldThrow<IllegalStateException> { builder.maxAttempts = 1 }
        shouldThrow<IllegalStateException> { builder.backoff = fixed(1.seconds) }
        shouldThrow<IllegalStateException> { builder.failures { record<IllegalStateException>() } }
        shouldThrow<IllegalStateException> { builder.results { failIfNull() } }
        shouldThrow<IllegalStateException> { builder.interval { _, _, _ -> null } }
        shouldThrow<IllegalStateException> { builder.isFailingAfterMaxAttempts = true }
        shouldThrow<IllegalStateException> { builder.onDiscardedResult = {} }
        shouldThrow<IllegalStateException> { builder.budget = null }
    }
})
