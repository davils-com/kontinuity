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

package com.davils.kontinuity.ratelimiter

import com.davils.kontinuity.core.filter.ResultFilter
import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_LIMIT: Int = 50
private const val DEFAULT_WAITING: Int = 10_000
private const val LIMIT: Int = 10
private const val BURST: Int = 20
private const val VIOLATIONS: Int = 5
private const val DAYS_IN_TEN_YEARS: Int = 3_650

class RateLimiterConfigTest : FunSpec({
    test("the default configuration has the defaults of resilience4j but a period of 500 milliseconds") {
        val config = RateLimiterConfig.Default
        config.limitForPeriod shouldBe DEFAULT_LIMIT
        config.limitRefreshPeriod shouldBe 500.milliseconds
        config.timeout shouldBe 5.seconds
        config.algorithm shouldBe RateLimiterAlgorithm.FixedWindow
        config.maxWaitingCallers shouldBe DEFAULT_WAITING
        config.drainOnFailures shouldBe null
        config.drainOnResults shouldBe ResultFilter.None
    }

    test("a configuration starts from its base") {
        val base = rateLimiterConfig { limitForPeriod = LIMIT }
        val derived = rateLimiterConfig(base) { algorithm = RateLimiterAlgorithm.SlidingWindow }
        derived.limitForPeriod shouldBe LIMIT
        derived.toString() shouldBe "RateLimiterConfig(limitForPeriod=10, limitRefreshPeriod=500ms, timeout=5s, " +
            "algorithm=SlidingWindow, maxWaitingCallers=10000)"
        rateLimiterConfig { drainOnFailures { record<IllegalStateException>() } }.drainOnFailures?.rules shouldBe 1
        rateLimiterConfig { drainOnResults { failIfNull() } }.drainOnResults.rules shouldBe 1
    }

    test("every value out of its bounds is reported at once") {
        val refusal = shouldThrow<ValidationException> {
            rateLimiterConfig {
                limitForPeriod = 0
                limitRefreshPeriod = Duration.ZERO
                timeout = -1.seconds
                maxWaitingCallers = -1
                algorithm = tokenBucket(1)
            }
        }
        refusal.report.violations.size shouldBe VIOLATIONS
    }

    test("every limit holds at its ceiling and fails past it") {
        val widest = rateLimiterConfig {
            limitForPeriod = RateLimiterConfig.HARD_LIMIT_FOR_PERIOD
            limitRefreshPeriod = RateLimiterConfig.HARD_REFRESH_PERIOD
            timeout = RateLimiterConfig.HARD_TIMEOUT
            maxWaitingCallers = RateLimiterConfig.HARD_WAITING_CALLERS
        }
        widest.limitRefreshPeriod shouldBe 1.days
        widest.timeout shouldBe 1.hours
        rateLimiterConfig { limitRefreshPeriod = 1.nanoseconds }.limitRefreshPeriod shouldBe 1.nanoseconds
        shouldThrow<ValidationException> {
            rateLimiterConfig {
                limitForPeriod =
                    RateLimiterConfig.HARD_LIMIT_FOR_PERIOD + 1
            }
        }
        shouldThrow<ValidationException> {
            rateLimiterConfig { limitRefreshPeriod = RateLimiterConfig.HARD_REFRESH_PERIOD + 1.nanoseconds }
        }
        shouldThrow<ValidationException> { rateLimiterConfig { limitRefreshPeriod = Duration.INFINITE } }
        shouldThrow<ValidationException> {
            rateLimiterConfig { timeout = RateLimiterConfig.HARD_TIMEOUT + 1.nanoseconds }
        }
        shouldThrow<ValidationException> {
            rateLimiterConfig { maxWaitingCallers = RateLimiterConfig.HARD_WAITING_CALLERS + 1 }
        }
        rateLimiterConfig { maxWaitingCallers = 0 }.maxWaitingCallers shouldBe 0
        rateLimiterConfig { timeout = Duration.ZERO }.timeout shouldBe Duration.ZERO
    }

    test("a token bucket holds at least the limit and refills within ten years") {
        tokenBucket(RateLimiterAlgorithm.HARD_BURST).toString() shouldBe "TokenBucket(burst=1000000)"
        shouldThrow<ValidationException> { tokenBucket(RateLimiterAlgorithm.HARD_BURST + 1) }
        shouldThrow<ValidationException> { tokenBucket(0) }
        rateLimiterConfig {
            limitForPeriod = LIMIT
            algorithm = tokenBucket(LIMIT)
        }.algorithm shouldBe tokenBucket(LIMIT)
        shouldThrow<ValidationException> {
            rateLimiterConfig {
                limitForPeriod = LIMIT
                algorithm = tokenBucket(LIMIT - 1)
            }
        }
        val slowest = rateLimiterConfig {
            limitForPeriod = 1
            limitRefreshPeriod = 1.days
            algorithm = tokenBucket(DAYS_IN_TEN_YEARS)
        }
        RateLimiterAlgorithm.HARD_REFILL shouldBe slowest.limitRefreshPeriod * DAYS_IN_TEN_YEARS
        shouldThrow<ValidationException> {
            rateLimiterConfig {
                limitForPeriod = 1
                limitRefreshPeriod = 1.days
                algorithm = tokenBucket(DAYS_IN_TEN_YEARS + 1)
            }
        }
    }

    test("algorithms are equal by value and show their names") {
        tokenBucket(BURST) shouldBe tokenBucket(BURST)
        tokenBucket(BURST).hashCode() shouldBe BURST
        tokenBucket(BURST) shouldNotBe tokenBucket(LIMIT)
        tokenBucket(BURST) shouldNotBe RateLimiterAlgorithm.FixedWindow
        RateLimiterAlgorithm.FixedWindow.toString() shouldBe "FixedWindow"
        RateLimiterAlgorithm.SlidingWindow.toString() shouldBe "SlidingWindow"
    }

    test("a builder cannot change after it was evaluated") {
        val builder = RateLimiterConfigBuilder()
        builder.build()
        shouldThrow<IllegalStateException> { builder.limitForPeriod = LIMIT }
        shouldThrow<IllegalStateException> { builder.limitRefreshPeriod = 1.seconds }
        shouldThrow<IllegalStateException> { builder.timeout = 1.seconds }
        shouldThrow<IllegalStateException> { builder.algorithm = RateLimiterAlgorithm.SlidingWindow }
        shouldThrow<IllegalStateException> { builder.maxWaitingCallers = 1 }
        shouldThrow<IllegalStateException> { builder.drainOnFailures { record<IllegalStateException>() } }
        shouldThrow<IllegalStateException> { builder.drainOnResults { failIfNull() } }
    }
})
