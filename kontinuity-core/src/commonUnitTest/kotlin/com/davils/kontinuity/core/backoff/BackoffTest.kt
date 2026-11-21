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

@file:OptIn(DavilsInternal::class)

package com.davils.kontinuity.core.backoff

import com.davils.konfetti.core.bus.Entropy
import com.davils.konfetti.testing.SeededEntropy
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SAMPLES: Int = 500
private const val SECOND_ATTEMPT: Int = 2
private const val THIRD_ATTEMPT: Int = 3
private const val LATE_ATTEMPT: Int = 40
private const val TRIPLE: Int = 3
private const val HALF: Double = 0.5
private const val ONE_AND_A_HALF: Double = 1.5
private val ONE_SECOND: Duration = 1.seconds
private val ZERO_ENTROPY: Entropy = Entropy { 0L }
private val TOP_ENTROPY: Entropy = Entropy { -1L }

class BackoffTest : FunSpec({
    test("a fixed backoff waits the same delay before every attempt") {
        val backoff = fixed(ONE_SECOND)
        backoff.delayBefore(attempt = SECOND_ATTEMPT, previous = Duration.ZERO, entropy = ZERO_ENTROPY) shouldBe
            ONE_SECOND
        backoff.delayBefore(attempt = LATE_ATTEMPT, previous = ONE_SECOND, entropy = ZERO_ENTROPY) shouldBe ONE_SECOND
        backoff.maximum shouldBe ONE_SECOND
        fixed(Duration.ZERO).maximum shouldBe Duration.ZERO
    }

    test("an exponential backoff grows by its multiplier up to its maximum") {
        val backoff = exponential(initial = ONE_SECOND, multiplier = 2.0, maximum = 5.seconds)
        backoff.delayBefore(attempt = 1, previous = Duration.ZERO, entropy = ZERO_ENTROPY) shouldBe ONE_SECOND
        backoff.delayBefore(attempt = SECOND_ATTEMPT, previous = Duration.ZERO, entropy = ZERO_ENTROPY) shouldBe
            ONE_SECOND
        backoff.delayBefore(attempt = THIRD_ATTEMPT, previous = ONE_SECOND, entropy = ZERO_ENTROPY) shouldBe 2.seconds
        backoff.delayBefore(attempt = LATE_ATTEMPT, previous = ONE_SECOND, entropy = ZERO_ENTROPY) shouldBe 5.seconds
    }

    test("an exponential backoff grows to 32 times its first delay by default, within the hard ceiling") {
        exponential(initial = ONE_SECOND).maximum shouldBe 32.seconds
        exponential(initial = 1.days).maximum shouldBe Backoff.HARD_MAXIMUM_DELAY
    }

    test("a proportional jitter stays within its factor") {
        val backoff = fixed(ONE_SECOND).jittered(proportional(HALF))
        val entropy = SeededEntropy(1L)
        repeat(SAMPLES) {
            val delay = backoff.delayBefore(attempt = SECOND_ATTEMPT, previous = Duration.ZERO, entropy = entropy)
            delay shouldBeGreaterThanOrEqualTo ONE_SECOND * HALF
            delay shouldBeLessThan ONE_SECOND * ONE_AND_A_HALF
        }
        backoff.maximum shouldBe ONE_SECOND * ONE_AND_A_HALF
    }

    test("a full jitter spreads over the whole delay and an equal jitter over its second half") {
        val full = fixed(ONE_SECOND).jittered(Jitter.Full)
        val equal = fixed(ONE_SECOND).jittered(Jitter.Equal)
        full.delayBefore(attempt = SECOND_ATTEMPT, previous = Duration.ZERO, entropy = ZERO_ENTROPY) shouldBe
            Duration.ZERO
        equal.delayBefore(attempt = SECOND_ATTEMPT, previous = Duration.ZERO, entropy = ZERO_ENTROPY) shouldBe
            ONE_SECOND * HALF
        val top = equal.delayBefore(attempt = SECOND_ATTEMPT, previous = Duration.ZERO, entropy = TOP_ENTROPY)
        top shouldBeLessThanOrEqualTo ONE_SECOND
        full.maximum shouldBe ONE_SECOND
    }

    test("a decorrelated jitter stays between the first delay and three times the previous one") {
        val backoff = exponential(initial = 100.milliseconds, maximum = 10.seconds).jittered(Jitter.Decorrelated)
        val entropy = SeededEntropy(2L)
        var previous = Duration.ZERO
        repeat(SAMPLES) { index ->
            val delay = backoff.delayBefore(attempt = index + SECOND_ATTEMPT, previous = previous, entropy = entropy)
            delay shouldBeGreaterThanOrEqualTo 100.milliseconds
            delay shouldBeLessThanOrEqualTo (previous * TRIPLE).coerceAtLeast(100.milliseconds)
            delay shouldBeLessThanOrEqualTo 10.seconds
            previous = delay
        }
    }

    test("a failing entropy is replaced by secure randomness") {
        val failing = Entropy { error("broken entropy") }
        val backoff = fixed(ONE_SECOND).jittered(Jitter.Full)
        val delay = backoff.delayBefore(
            attempt = SECOND_ATTEMPT,
            previous = Duration.ZERO,
            entropy = failing,
        )
        delay shouldBeLessThan ONE_SECOND
    }

    test("values out of their bounds are refused") {
        shouldThrow<ValidationException> { fixed(-ONE_SECOND) }
        shouldThrow<ValidationException> { fixed(Duration.INFINITE) }
        shouldThrow<ValidationException> { fixed(Backoff.HARD_MAXIMUM_DELAY + ONE_SECOND) }
        shouldThrow<ValidationException> { exponential(initial = Duration.ZERO) }
        shouldThrow<ValidationException> { exponential(initial = ONE_SECOND, multiplier = HALF) }
        shouldThrow<ValidationException> { exponential(initial = ONE_SECOND, multiplier = Backoff.HARD_MULTIPLIER + 1) }
        shouldThrow<ValidationException> { exponential(initial = 2.seconds, maximum = ONE_SECOND) }
        shouldThrow<ValidationException> { fixed(ONE_SECOND).jittered(proportional(0.0)) }
        shouldThrow<ValidationException> { fixed(ONE_SECOND).jittered(proportional(ONE_AND_A_HALF)) }
        fixed(ONE_SECOND).jittered(proportional(1.0)).maximum shouldBe 2.seconds
    }

    test("backoffs are equal by value and show their values") {
        fixed(ONE_SECOND) shouldBe fixed(ONE_SECOND)
        fixed(ONE_SECOND).hashCode() shouldBe fixed(ONE_SECOND).hashCode()
        fixed(ONE_SECOND) shouldNotBe fixed(2.seconds)
        fixed(ONE_SECOND).toString() shouldBe "Fixed(1s)"
        val exponential = exponential(initial = ONE_SECOND)
        exponential shouldBe exponential(initial = ONE_SECOND)
        exponential.hashCode() shouldBe exponential(initial = ONE_SECOND).hashCode()
        exponential shouldNotBe exponential(initial = ONE_SECOND, multiplier = TRIPLE.toDouble())
        exponential shouldNotBe exponential(initial = 2.seconds)
        exponential shouldNotBe exponential(initial = ONE_SECOND, maximum = 2.seconds)
        exponential shouldNotBe fixed(ONE_SECOND)
        exponential.toString() shouldBe "Exponential(initial=1s, multiplier=2.0, maximum=32s)"
        val jittered = fixed(ONE_SECOND).jittered(proportional())
        jittered shouldBe fixed(ONE_SECOND).jittered(proportional())
        val same = fixed(ONE_SECOND).jittered(proportional())
        jittered.hashCode() shouldBe same.hashCode()
        jittered shouldNotBe fixed(ONE_SECOND).jittered(Jitter.Full)
        jittered shouldNotBe fixed(2.seconds).jittered(proportional())
        jittered shouldNotBe fixed(ONE_SECOND)
        jittered.toString() shouldBe "Jittered(Fixed(1s), Proportional(0.5))"
        proportional() shouldNotBe Jitter.Full
        listOf(Jitter.Full, Jitter.Equal, Jitter.Decorrelated).map(Jitter::toString) shouldBe
            listOf("Full", "Equal", "Decorrelated")
    }
})
