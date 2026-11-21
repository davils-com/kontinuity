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

import com.davils.kontinuity.core.backoff.fixed
import com.davils.kontinuity.retry.fixture.BROKEN
import com.davils.kontinuity.retry.fixture.broken
import com.davils.kontinuity.retry.fixture.quickRetry
import com.davils.kontinuity.retry.fixture.virtualEnvironment
import com.davils.kontinuity.testing.recordEvents
import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_BALANCE: Long = 100L
private const val DEFAULT_RATIO: Double = 0.2
private const val DEFAULT_RATE: Double = 10.0
private const val CALLS: Int = 4
private const val HALF: Double = 0.5
private const val RESERVE: Long = 2L
private const val VIOLATIONS: Int = 3
private const val NEGATIVE: Double = -1.0
private const val FAILING: Int = -1
private const val ATTEMPTS: Int = 5

@OptIn(ExperimentalKotest::class)
class RetryBudgetTest : FunSpec({
    coroutineTestScope = true

    test("a budget has the defaults of Finagle and gRPC") {
        val budget = retryBudget(virtualEnvironment())
        budget.ratio shouldBe DEFAULT_RATIO
        budget.minRetriesPerSecond shouldBe DEFAULT_RATE
        budget.window shouldBe 10.seconds
        budget.balance shouldBe DEFAULT_BALANCE
        budget.toString() shouldBe "RetryBudget(ratio=0.2, minRetriesPerSecond=10.0, window=10s, balance=100)"
    }

    test("the reserve is spent and then every retry is denied") {
        val budget = retryBudget(virtualEnvironment()) {
            ratio = 0.0
            minRetriesPerSecond = 1.0
            window = 2.seconds
        }
        budget.balance shouldBe RESERVE
        repeat(RESERVE.toInt()) { budget.tryWithdraw() shouldBe true }
        budget.tryWithdraw() shouldBe false
        budget.balance shouldBe 0L
    }

    test("a share of the calls is granted as retries") {
        val budget = retryBudget(virtualEnvironment()) {
            ratio = HALF
            minRetriesPerSecond = 0.0
        }
        budget.tryWithdraw() shouldBe false
        repeat(CALLS) { budget.deposit() }
        budget.balance shouldBe (CALLS / 2).toLong()
        repeat(CALLS / 2) { budget.tryWithdraw() shouldBe true }
        budget.tryWithdraw() shouldBe false
    }

    test("the spent retries leave the window and the reserve returns") {
        val budget = retryBudget(virtualEnvironment()) {
            ratio = 0.0
            minRetriesPerSecond = 1.0
            window = 2.seconds
        }
        repeat(RESERVE.toInt()) { budget.tryWithdraw() }
        delay(1.seconds)
        budget.balance shouldBe 0L
        delay(1.seconds + 1.milliseconds)
        budget.balance shouldBe RESERVE
    }

    test("the calls leave the window, too, and reading changes nothing") {
        val budget = retryBudget(virtualEnvironment()) {
            ratio = 1.0
            minRetriesPerSecond = 0.0
            window = 1.seconds
        }
        repeat(CALLS) { budget.deposit() }
        budget.balance shouldBe CALLS.toLong()
        budget.balance shouldBe CALLS.toLong()
        delay(2.seconds)
        budget.balance shouldBe 0L
        budget.tryWithdraw() shouldBe false
    }

    test("every value is bounded by its hard ceiling") {
        val environment = virtualEnvironment()
        retryBudget(environment) { ratio = RetryBudget.HARD_RATIO }.ratio shouldBe RetryBudget.HARD_RATIO
        val fastest = retryBudget(environment) { minRetriesPerSecond = RetryBudget.HARD_MIN_RETRIES_PER_SECOND }
        fastest.minRetriesPerSecond shouldBe RetryBudget.HARD_MIN_RETRIES_PER_SECOND
        retryBudget(environment) { window = RetryBudget.HARD_WINDOW }.window shouldBe RetryBudget.HARD_WINDOW
        shouldThrow<ValidationException> { retryBudget(environment) { ratio = RetryBudget.HARD_RATIO + 1.0 } }
        shouldThrow<ValidationException> {
            retryBudget(environment) { minRetriesPerSecond = RetryBudget.HARD_MIN_RETRIES_PER_SECOND + 1.0 }
        }
        shouldThrow<ValidationException> { retryBudget(environment) { window = RetryBudget.HARD_WINDOW + 1.hours } }
        val refusal = shouldThrow<ValidationException> {
            retryBudget(environment) {
                ratio = Double.NaN
                minRetriesPerSecond = NEGATIVE
                window = Duration.ZERO
            }
        }
        refusal.report.violations.size shouldBe VIOLATIONS
        shouldThrow<ValidationException> { retryBudget(environment) { ratio = NEGATIVE } }
        shouldThrow<ValidationException> { retryBudget(environment) { window = Duration.INFINITE } }
    }

    test("a budget builder cannot change after it was evaluated") {
        val builder = RetryBudgetBuilder(virtualEnvironment().timeSource)
        builder.build()
        shouldThrow<IllegalStateException> { builder.ratio = HALF }
        shouldThrow<IllegalStateException> { builder.minRetriesPerSecond = HALF }
        shouldThrow<IllegalStateException> { builder.window = 1.seconds }
    }

    test("a denied retry rethrows the last failure and tells why") {
        val environment = virtualEnvironment()
        val budget = retryBudget(environment) {
            ratio = 0.0
            minRetriesPerSecond = 0.0
        }
        val retry = quickRetry(environment) { this.budget = budget }
        val log = recordEvents(retry.events)
        var attempts = 0
        val failure = shouldThrow<IllegalStateException> {
            retry.execute {
                attempts++
                broken()
            }
        }
        failure.message shouldBe BROKEN
        attempts shouldBe 1
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.only<RetryEvent.BudgetDenied>().single().attempt shouldBe 2
        log.only<RetryEvent.Exhausted>().single().attempts shouldBe 1
        retry.snapshot().failedWithoutRetry shouldBe 1L
    }

    test("a denied retry of a result returns the last result, even when exhaustion would fail") {
        val environment = virtualEnvironment()
        val budget = retryBudget(environment) {
            ratio = 0.0
            minRetriesPerSecond = 0.0
        }
        val retry = quickRetry(environment) {
            this.budget = budget
            isFailingAfterMaxAttempts = true
            results { failIf<Int> { value -> value < 0 } }
        }
        retry.execute { FAILING } shouldBe FAILING
        retry.snapshot().failedWithoutRetry shouldBe 1L
    }

    test("retries that share a budget pay for each other, but never for failures they would not repeat") {
        val environment = virtualEnvironment()
        val budget = retryBudget(environment) {
            ratio = 0.0
            minRetriesPerSecond = 1.0
            window = 2.seconds
        }
        val first = quickRetry(environment) {
            this.budget = budget
            backoff = fixed(Duration.ZERO)
            maxAttempts = ATTEMPTS
            failures { ignore<IllegalArgumentException>() }
        }
        val second = quickRetry(environment) {
            this.budget = budget
            backoff = fixed(Duration.ZERO)
        }
        shouldThrow<IllegalArgumentException> { first.execute { throw IllegalArgumentException(BROKEN) } }
        budget.balance shouldBe RESERVE
        shouldThrow<IllegalStateException> { first.execute { broken() } }
        first.snapshot().retries shouldBe RESERVE
        shouldThrow<IllegalStateException> { second.execute { broken() } }
        second.snapshot().retries shouldBe 0L
    }

    test("the last attempt spends nothing from the budget") {
        val environment = virtualEnvironment()
        val budget = retryBudget(environment) {
            ratio = 0.0
            minRetriesPerSecond = 1.0
            window = 2.seconds
        }
        val retry = quickRetry(environment) {
            this.budget = budget
            maxAttempts = 2
        }
        shouldThrow<IllegalStateException> { retry.execute { broken() } }
        budget.balance shouldBe RESERVE - 1L
    }
})
