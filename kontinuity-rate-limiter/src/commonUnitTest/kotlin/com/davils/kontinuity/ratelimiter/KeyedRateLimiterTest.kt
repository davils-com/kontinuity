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

import com.davils.kollect.core.MeterRegistry
import com.davils.kollect.core.meterRegistry
import com.davils.kontinuity.ratelimiter.fixture.LIMIT
import com.davils.kontinuity.ratelimiter.fixture.PERIOD
import com.davils.kontinuity.ratelimiter.fixture.gauge
import com.davils.kontinuity.ratelimiter.fixture.sum
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

private const val NAME: String = "per-principal"
private const val ALICE: String = "alice"
private const val BOB: String = "bob"
private const val MALLORY: String = "mallory"
private const val CALLS: String = "kontinuity.keyed_rate_limiter.calls"
private const val KEYS: String = "kontinuity.keyed_rate_limiter.keys"

private fun TestScope.keyed(
    maxKeys: Int = KeyedRateLimiter.DEFAULT_MAX_KEYS,
    idleTimeout: Duration = PERIOD * 2,
    registry: MeterRegistry? = null,
): KeyedRateLimiter<String> {
    val environment = testEnvironment(testCoroutineScheduler.timeSource) {
        registry?.let { meters -> meters(meters) }
    }
    return keyedRateLimiter(name = NAME, maxKeys = maxKeys, idleTimeout = idleTimeout, environment = environment) {
        limitForPeriod = LIMIT
        limitRefreshPeriod = PERIOD
        timeout = Duration.ZERO
    }
}

@OptIn(ExperimentalKotest::class)
class KeyedRateLimiterTest : FunSpec({
    coroutineTestScope = true

    context("the limit of a key") {
        test("is spent by that key alone") {
            val limiter = keyed()

            repeat(LIMIT) { limiter.tryAcquire(ALICE) shouldBe true }

            limiter.tryAcquire(ALICE) shouldBe false
            limiter.tryAcquire(BOB) shouldBe true
            limiter.keys shouldBe 2
        }

        test("refuses a call of a key over its limit and runs the others") {
            val limiter = keyed()
            repeat(LIMIT) { limiter.execute(ALICE) { } }

            shouldThrow<RequestNotPermittedException> { limiter.execute(ALICE) { } }
            limiter.execute(BOB) { BOB } shouldBe BOB
        }

        test("comes back with the next period") {
            val limiter = keyed()
            repeat(LIMIT) { limiter.tryAcquire(ALICE) }

            delay(PERIOD)

            limiter.tryAcquire(ALICE) shouldBe true
        }
    }

    context("the bound of the keys") {
        test("refuses a new key while every key is in use, so a flood of keys fails closed") {
            val limiter = keyed(maxKeys = 2)
            limiter.tryAcquire(ALICE)
            limiter.tryAcquire(BOB)

            limiter.tryAcquire(MALLORY) shouldBe false
            val refusal = shouldThrow<RequestNotPermittedException> { limiter.execute(MALLORY) { } }

            refusal.availableIn shouldBe PERIOD * 2
            limiter.keys shouldBe 2
            limiter.tryAcquire(ALICE) shouldBe true
        }

        test("forgets idle keys to make room and keeps the ones in use") {
            val limiter = keyed(maxKeys = 2)
            limiter.tryAcquire(ALICE)
            delay(PERIOD)
            limiter.tryAcquire(BOB)
            delay(PERIOD + 1.milliseconds)

            limiter.tryAcquire(MALLORY) shouldBe true

            limiter.keys shouldBe 2
            limiter.tryAcquire(ALICE) shouldBe false
        }

        test("tells a refused key when the next key may be forgotten") {
            val limiter = keyed(maxKeys = 1)
            limiter.tryAcquire(ALICE)
            delay(PERIOD * 2 + 1.milliseconds)
            limiter.tryAcquire(BOB) shouldBe true
            delay(PERIOD)

            val refusal = shouldThrow<RequestNotPermittedException> { limiter.execute(MALLORY) { } }

            refusal.availableIn shouldBe PERIOD
        }
    }

    context("the bounds of the limiter") {
        test("refuse a number of keys or an idle timeout out of range") {
            shouldThrow<IllegalArgumentException> { keyed(maxKeys = 0) }
            shouldThrow<IllegalArgumentException> { keyed(maxKeys = KeyedRateLimiter.HARD_MAX_KEYS + 1) }
            shouldThrow<IllegalArgumentException> { keyed(idleTimeout = PERIOD / 2) }
            shouldThrow<IllegalArgumentException> { keyed(idleTimeout = 2.days) }
        }
    }

    context("the telemetry") {
        test("counts calls by outcome and the keys without a series per key") {
            val registry = meterRegistry()
            val limiter = keyed(maxKeys = 1, registry = registry)
            repeat(LIMIT + 1) { limiter.tryAcquire(ALICE) }
            limiter.tryAcquire(BOB)

            registry.sum(CALLS, NAME, "permitted") shouldBe LIMIT.toDouble()
            registry.sum(CALLS, NAME, "rejected") shouldBe 1.0
            registry.sum(CALLS, NAME, "overflowed") shouldBe 1.0
            registry.gauge(KEYS, NAME) shouldBe 1.0
            limiter.toString() shouldContain "keys=1/1"
        }

        test("counts the calls it runs and refuses") {
            val registry = meterRegistry()
            val limiter = keyed(registry = registry)
            repeat(LIMIT) { limiter.execute(ALICE) { } }
            shouldThrow<RequestNotPermittedException> { limiter.execute(ALICE) { } }

            registry.sum(CALLS, NAME, "permitted") shouldBe LIMIT.toDouble()
            registry.sum(CALLS, NAME, "rejected") shouldBe 1.0
        }
    }
})
