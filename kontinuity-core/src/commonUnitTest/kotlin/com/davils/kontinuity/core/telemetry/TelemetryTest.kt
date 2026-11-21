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

package com.davils.kontinuity.core.telemetry

import com.davils.konfetti.core.bus.Entropy
import com.davils.konfetti.testing.SeededEntropy
import com.davils.kore.core.annotation.DavilsInternal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

private const val SAMPLES: Int = 1_000
private const val ANSWER: Int = 42

class TelemetryTest : FunSpec({
    test("a guard runs its block and returns its result") {
        guard().locked { ANSWER } shouldBe ANSWER
    }

    test("a guard can be taken again by its holder") {
        val lock = guard()
        lock.locked { lock.locked { ANSWER } } shouldBe ANSWER
    }

    test("fractions are uniform in [0, 1)") {
        val entropy = SeededEntropy(1L)
        repeat(SAMPLES) {
            val fraction = entropy.nextFraction()
            fraction shouldBeGreaterThanOrEqual 0.0
            fraction shouldBeLessThan 1.0
        }
        Entropy { -1L }.nextFraction() shouldBeLessThan 1.0
        Entropy { 0L }.nextFraction() shouldBe 0.0
    }

    test("a failing entropy falls back to secure randomness") {
        val failing = Entropy { error("broken entropy") }
        val fraction = failing.nextFraction()
        fraction shouldBeGreaterThanOrEqual 0.0
        fraction shouldBeLessThan 1.0
    }

    test("secure entropy does not repeat itself") {
        SecureEntropy.nextLong() shouldNotBe SecureEntropy.nextLong()
    }
})
