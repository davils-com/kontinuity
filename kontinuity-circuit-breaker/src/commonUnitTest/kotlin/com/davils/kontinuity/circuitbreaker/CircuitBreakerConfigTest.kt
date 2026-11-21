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

import com.davils.kontinuity.core.backoff.fixed
import com.davils.kontinuity.core.filter.FailureFilter
import com.davils.kontinuity.core.filter.ResultFilter
import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TRIALS: Int = 3
private const val THRESHOLD: Double = 30.0
private const val SIZE: Int = 10
private const val VIOLATIONS: Int = 7
private const val DEFAULT_RATE: Double = 50.0
private const val ALL: Double = 100.0
private const val DEFAULT_CALLS: Int = 100
private const val DEFAULT_TRIALS: Int = 10
private const val BUCKETS_PER_MINUTE: Int = 60

class CircuitBreakerConfigTest : FunSpec({
    test("the default configuration has the defaults of resilience4j") {
        val config = CircuitBreakerConfig.Default
        config.failureRateThreshold shouldBe DEFAULT_RATE
        config.slowCallRateThreshold shouldBe ALL
        config.slowCallDurationThreshold shouldBe 1.minutes
        config.slidingWindow shouldBe countBased(DEFAULT_CALLS)
        config.minimumNumberOfCalls shouldBe DEFAULT_CALLS
        config.permittedCallsInHalfOpenState shouldBe DEFAULT_TRIALS
        config.maxWaitInHalfOpenState shouldBe Duration.INFINITE
        config.stateAfterHalfOpenTimeout shouldBe CircuitState.Open
        config.waitInOpenState shouldBe fixed(1.minutes)
        config.isAutomaticTransitionEnabled shouldBe false
        config.failures shouldBe FailureFilter.RecordAll
        config.results shouldBe ResultFilter.None
        config.openingFailures shouldBe null
        config.openingResults shouldBe ResultFilter.None
    }

    test("a configuration starts from its base") {
        val base = circuitBreakerConfig { failureRateThreshold = THRESHOLD }
        val derived = circuitBreakerConfig(base) { permittedCallsInHalfOpenState = TRIALS }
        derived.failureRateThreshold shouldBe THRESHOLD
        derived.permittedCallsInHalfOpenState shouldBe TRIALS
        derived.toString() shouldBe "CircuitBreakerConfig(failureRateThreshold=30.0, slowCallRateThreshold=100.0, " +
            "slowCallDurationThreshold=1m, slidingWindow=CountBased(100), minimumNumberOfCalls=100, " +
            "permittedCallsInHalfOpenState=3, waitInOpenState=Fixed(1m))"
    }

    test("every value out of its bounds is reported at once") {
        val refusal = shouldThrow<ValidationException> {
            circuitBreakerConfig {
                failureRateThreshold = 0.0
                slowCallRateThreshold = THRESHOLD * SIZE
                slowCallDurationThreshold = Duration.ZERO
                slidingWindow = countBased(SIZE)
                minimumNumberOfCalls = SIZE + 1
                permittedCallsInHalfOpenState = 0
                maxWaitInHalfOpenState = Duration.ZERO
                stateAfterHalfOpenTimeout = CircuitState.HalfOpen
            }
        }
        refusal.report.violations.size shouldBe VIOLATIONS
        shouldThrow<ValidationException> { circuitBreakerConfig { slowCallDurationThreshold = 2.days } }
        shouldThrow<ValidationException> {
            circuitBreakerConfig { permittedCallsInHalfOpenState = CircuitBreakerConfig.HARD_TRIAL_CALLS + 1 }
        }
    }

    test("a time-based window allows any minimum of calls") {
        val config = circuitBreakerConfig {
            slidingWindow = timeBased(length = 1.minutes)
            minimumNumberOfCalls = SlidingWindow.HARD_SIZE * 2
        }
        config.minimumNumberOfCalls shouldBe SlidingWindow.HARD_SIZE * 2
    }

    test("windows are bounded and equal by value") {
        countBased(SlidingWindow.HARD_SIZE).toString() shouldBe "CountBased(100000)"
        shouldThrow<ValidationException> { countBased(0) }
        shouldThrow<ValidationException> { countBased(SlidingWindow.HARD_SIZE + 1) }
        timeBased(1.minutes) shouldBe timeBased(length = 1.minutes, buckets = BUCKETS_PER_MINUTE)
        timeBased(1.minutes).hashCode() shouldBe timeBased(length = 1.minutes, buckets = BUCKETS_PER_MINUTE).hashCode()
        timeBased(1.minutes) shouldNotBe timeBased(length = 1.minutes, buckets = TRIALS)
        timeBased(1.minutes) shouldNotBe timeBased(2.minutes)
        timeBased(1.minutes) shouldNotBe countBased(SIZE)
        countBased(SIZE) shouldNotBe timeBased(1.minutes)
        countBased(SIZE).hashCode() shouldBe SIZE
        timeBased(1.seconds / 2).toString() shouldBe "TimeBased(500ms, buckets=1)"
        val longest = timeBased(SlidingWindow.HARD_LENGTH) as SlidingWindow.TimeBased
        longest.buckets shouldBe SlidingWindow.HARD_BUCKETS
        shouldThrow<ValidationException> { timeBased(Duration.ZERO) }
        shouldThrow<ValidationException> { timeBased(SlidingWindow.HARD_LENGTH + 1.hours) }
        shouldThrow<ValidationException> { timeBased(length = 1.minutes, buckets = SlidingWindow.HARD_BUCKETS + 1) }
    }

    test("a builder cannot change after it was evaluated") {
        val builder = CircuitBreakerConfigBuilder()
        builder.build()
        shouldThrow<IllegalStateException> { builder.failureRateThreshold = THRESHOLD }
        shouldThrow<IllegalStateException> { builder.slowCallRateThreshold = THRESHOLD }
        shouldThrow<IllegalStateException> { builder.slowCallDurationThreshold = 1.seconds }
        shouldThrow<IllegalStateException> { builder.slidingWindow = countBased(SIZE) }
        shouldThrow<IllegalStateException> { builder.minimumNumberOfCalls = 1 }
        shouldThrow<IllegalStateException> { builder.permittedCallsInHalfOpenState = 1 }
        shouldThrow<IllegalStateException> { builder.maxWaitInHalfOpenState = 1.seconds }
        shouldThrow<IllegalStateException> { builder.stateAfterHalfOpenTimeout = CircuitState.Closed }
        shouldThrow<IllegalStateException> { builder.waitInOpenState = fixed(1.seconds) }
        shouldThrow<IllegalStateException> { builder.isAutomaticTransitionEnabled = true }
        shouldThrow<IllegalStateException> { builder.failures { record<IllegalStateException>() } }
        shouldThrow<IllegalStateException> { builder.results { failIfNull() } }
        shouldThrow<IllegalStateException> { builder.openOnFailures { record<IllegalStateException>() } }
        shouldThrow<IllegalStateException> { builder.openOnResults { failIfNull() } }
    }
})
