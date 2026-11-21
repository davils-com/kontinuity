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

import com.davils.kollect.core.meterRegistry
import com.davils.kontinuity.hedge.fixture.HEDGE
import com.davils.kontinuity.hedge.fixture.Script
import com.davils.kontinuity.hedge.fixture.gauge
import com.davils.kontinuity.hedge.fixture.pass
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val WINDOW: Int = 4
private const val MEDIAN: Double = 0.5
private const val STEP: Long = 100L
private const val FIRST: Long = 100L
private const val SECOND: Long = 200L
private const val THIRD: Long = 300L
private const val FOURTH: Long = 400L
private const val FIFTH: Long = 500L
private const val HOURS: Int = 3
private const val THIRD_IN_SECONDS: Double = 0.3
private const val DELAY_GAUGE: String = "kontinuity.hedge.delay"

@OptIn(ExperimentalKotest::class)
class HedgePercentileTest : FunSpec({
    coroutineTestScope = true

    test("a percentile delay waits the fallback until it has enough latencies, then follows them") {
        val registry = meterRegistry()
        val environment = testEnvironment(testCoroutineScheduler.timeSource) { meters(registry) }
        val hedge = hedge(HEDGE, environment) {
            delay = percentileDelay(percentile = MEDIAN, minimumSamples = 2, window = WINDOW, fallback = 1.seconds)
        }
        hedge.delay shouldBe 1.seconds
        registry.gauge(DELAY_GAUGE, HEDGE) shouldBe 1.0
        hedge.execute { delay(FIRST.milliseconds) }
        hedge.delay shouldBe 1.seconds
        hedge.execute { delay(SECOND.milliseconds) }
        hedge.delay shouldBe FIRST.milliseconds
        hedge.execute { delay(FOURTH.milliseconds) }
        hedge.execute { delay(THIRD.milliseconds) }
        hedge.delay shouldBe SECOND.milliseconds
        hedge.execute { delay(FIFTH.milliseconds) }
        hedge.delay shouldBe THIRD.milliseconds
        hedge.snapshot().delay shouldBe THIRD.milliseconds
        registry.gauge(DELAY_GAUGE, HEDGE) shouldBe THIRD_IN_SECONDS
    }

    test("a percentile delay starts the copies after the observed latency") {
        val timeSource = testCoroutineScheduler.timeSource
        val hedge = hedge(HEDGE, testEnvironment(timeSource)) {
            delay = percentileDelay(percentile = 1.0, minimumSamples = 1, window = 1, fallback = 1.seconds)
        }
        hedge.execute { delay(STEP.milliseconds) }
        val script = Script(pass(1.seconds), pass(STEP.milliseconds))
        val mark = timeSource.markNow()
        hedge.execute { script.run() } shouldBe 1
        mark.elapsedNow() shouldBe (STEP * 2).milliseconds
        hedge.delay shouldBe STEP.milliseconds
    }

    test("a percentile delay stays within its floor and its ceiling") {
        val hedge = hedge(HEDGE, testEnvironment(testCoroutineScheduler.timeSource)) {
            delay = percentileDelay(minimumSamples = 1, window = 1, fallback = HedgeDelay.HARD_DELAY)
        }
        hedge.execute { }
        hedge.delay shouldBe HedgeDelay.MINIMUM_DELAY
        val script = Script(pass(2.hours), pass(HOURS.hours))
        hedge.execute { script.run() } shouldBe 0
        hedge.delay shouldBe HedgeDelay.HARD_DELAY
    }

    test("a ring of the largest size keeps its latencies in order") {
        val hedge = hedge(HEDGE, testEnvironment(testCoroutineScheduler.timeSource)) {
            delay = percentileDelay(percentile = 1.0, minimumSamples = 1, window = HedgeDelay.HARD_WINDOW)
        }
        repeat(WINDOW) { index -> hedge.execute { delay(STEP.milliseconds * (WINDOW - index)) } }
        hedge.delay shouldBe STEP.milliseconds * WINDOW
        hedge.execute { delay(Duration.ZERO) }
        hedge.delay shouldBe STEP.milliseconds * WINDOW
    }
})
