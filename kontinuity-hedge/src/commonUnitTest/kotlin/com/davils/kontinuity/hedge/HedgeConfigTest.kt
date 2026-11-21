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

package com.davils.kontinuity.hedge

import com.davils.kontinuity.core.filter.FailureFilter
import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private const val ATTEMPTS: Int = 3
private const val DEFAULT_PERCENTILE: Double = 0.95
private const val DEFAULT_SAMPLES: Int = 100
private const val DEFAULT_WINDOW: Int = 1_000
private const val DEFAULT_DELAY: Long = 100L
private const val PERCENTILE: Double = 0.99
private const val SAMPLES: Int = 10
private const val WINDOW: Int = 20
private const val TOO_MUCH: Double = 1.5
private const val PERCENTILE_VIOLATIONS: Int = 4

class HedgeConfigTest : FunSpec({
    test("the default configuration hedges once after 100 milliseconds") {
        val config = HedgeConfig.Default
        config.delay shouldBe fixedDelay(DEFAULT_DELAY.milliseconds)
        config.delay.initial shouldBe DEFAULT_DELAY.milliseconds
        config.maxHedgedAttempts shouldBe 1
        config.failures shouldBe FailureFilter.RecordAll
        config.toString() shouldBe "HedgeConfig(delay=Fixed(100ms), maxHedgedAttempts=1, " +
            "failures=FailureFilter(ignored=0, recorded=0, causes=true))"
    }

    test("a configuration starts from its base") {
        val base = hedgeConfig { maxHedgedAttempts = ATTEMPTS }
        val derived = hedgeConfig(base) { delay = fixedDelay(1.seconds) }
        derived.maxHedgedAttempts shouldBe ATTEMPTS
        derived.delay shouldBe fixedDelay(1.seconds)
        hedgeConfig(derived) { failures { ignore<IllegalStateException>() } }.failures.rules shouldBe 1
    }

    test("the number of hedged copies is bounded at its ceiling") {
        hedgeConfig { maxHedgedAttempts = HedgeConfig.HARD_HEDGED_ATTEMPTS }.maxHedgedAttempts shouldBe
            HedgeConfig.HARD_HEDGED_ATTEMPTS
        shouldThrow<ValidationException> { hedgeConfig { maxHedgedAttempts = HedgeConfig.HARD_HEDGED_ATTEMPTS + 1 } }
        shouldThrow<ValidationException> { hedgeConfig { maxHedgedAttempts = 0 } }
    }

    test("a fixed delay is positive and at most its ceiling") {
        fixedDelay(HedgeDelay.HARD_DELAY).initial shouldBe HedgeDelay.HARD_DELAY
        shouldThrow<ValidationException> { fixedDelay(HedgeDelay.HARD_DELAY + 1.nanoseconds) }
        shouldThrow<ValidationException> { fixedDelay(Duration.ZERO) }
        shouldThrow<ValidationException> { fixedDelay(-1.seconds) }
        shouldThrow<ValidationException> { fixedDelay(Duration.INFINITE) }
    }

    test("a percentile delay has the defaults of a percentile of 95 percent") {
        val delay = percentileDelay() as HedgeDelay.Percentile
        delay.percentile shouldBe DEFAULT_PERCENTILE
        delay.minimumSamples shouldBe DEFAULT_SAMPLES
        delay.window shouldBe DEFAULT_WINDOW
        delay.fallback shouldBe DEFAULT_DELAY.milliseconds
        delay.initial shouldBe DEFAULT_DELAY.milliseconds
        delay.toString() shouldBe "Percentile(0.95, minimumSamples=100, window=1000, fallback=100ms)"
    }

    test("a percentile delay is bounded at every ceiling") {
        percentileDelay(percentile = 1.0, minimumSamples = 1, window = 1).toString() shouldBe
            "Percentile(1.0, minimumSamples=1, window=1, fallback=100ms)"
        val largest = percentileDelay(
            minimumSamples = HedgeDelay.HARD_WINDOW,
            window = HedgeDelay.HARD_WINDOW,
            fallback = HedgeDelay.HARD_DELAY,
        )
        largest.initial shouldBe HedgeDelay.HARD_DELAY
        shouldThrow<ValidationException> { percentileDelay(window = HedgeDelay.HARD_WINDOW + 1) }
        shouldThrow<ValidationException> { percentileDelay(fallback = HedgeDelay.HARD_DELAY + 1.nanoseconds) }
        shouldThrow<ValidationException> { percentileDelay(minimumSamples = WINDOW + 1, window = WINDOW) }
        shouldThrow<ValidationException> { percentileDelay(percentile = TOO_MUCH) }
        shouldThrow<ValidationException> { percentileDelay(percentile = Double.NaN) }
        val refusal = shouldThrow<ValidationException> {
            percentileDelay(percentile = 0.0, minimumSamples = 0, window = 0, fallback = Duration.ZERO)
        }
        refusal.report.violations.size shouldBe PERCENTILE_VIOLATIONS
    }

    test("delays are equal by value") {
        fixedDelay(1.seconds) shouldBe fixedDelay(1.seconds)
        fixedDelay(1.seconds).hashCode() shouldBe fixedDelay(1.seconds).hashCode()
        fixedDelay(1.seconds) shouldNotBe fixedDelay(2.seconds)
        fixedDelay(1.seconds) shouldNotBe percentileDelay()
        percentileDelay() shouldBe percentileDelay()
        percentileDelay().hashCode() shouldBe percentileDelay().hashCode()
        percentileDelay() shouldNotBe fixedDelay(1.seconds)
        percentileDelay() shouldNotBe percentileDelay(percentile = PERCENTILE)
        percentileDelay() shouldNotBe percentileDelay(minimumSamples = SAMPLES)
        percentileDelay() shouldNotBe percentileDelay(window = WINDOW, minimumSamples = SAMPLES)
        percentileDelay(window = WINDOW, minimumSamples = SAMPLES) shouldNotBe
            percentileDelay(window = WINDOW + 1, minimumSamples = SAMPLES)
        percentileDelay() shouldNotBe percentileDelay(fallback = 1.seconds)
    }

    test("a builder cannot change after it was evaluated") {
        val builder = HedgeConfigBuilder()
        builder.build()
        shouldThrow<IllegalStateException> { builder.delay = fixedDelay(1.seconds) }
        shouldThrow<IllegalStateException> { builder.maxHedgedAttempts = ATTEMPTS }
        shouldThrow<IllegalStateException> { builder.failures = FailureFilter.RecordAll }
        shouldThrow<IllegalStateException> { builder.failures { record<IllegalStateException>() } }
    }
})
