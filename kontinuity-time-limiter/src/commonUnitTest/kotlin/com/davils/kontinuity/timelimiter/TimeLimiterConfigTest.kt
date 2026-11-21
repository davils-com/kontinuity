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

package com.davils.kontinuity.timelimiter

import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private const val DETACHED: Int = 5
private const val HASH_FACTOR: Int = 31

class TimeLimiterConfigTest : FunSpec({
    test("the default configuration has the defaults of resilience4j") {
        val config = TimeLimiterConfig.Default
        config.timeout shouldBe 1.seconds
        config.strategy shouldBe TimeoutStrategy.Hard
        config.toString() shouldBe "TimeLimiterConfig(timeout=1s, strategy=Hard)"
    }

    test("a configuration starts from its base") {
        val base = timeLimiterConfig { timeout = 2.seconds }
        val derived = timeLimiterConfig(base) { strategy = TimeoutStrategy.Soft(maxDetached = DETACHED) }
        derived.timeout shouldBe 2.seconds
        derived.toString() shouldBe
            "TimeLimiterConfig(timeout=2s, strategy=Soft(isCancellingOnTimeout=true, maxDetached=5))"
    }

    test("a timeout is positive and at most one day or infinite") {
        timeLimiterConfig { timeout = TimeLimiterConfig.HARD_TIMEOUT }.timeout shouldBe 1.days
        timeLimiterConfig { timeout = 1.nanoseconds }.timeout shouldBe 1.nanoseconds
        timeLimiterConfig { timeout = Duration.INFINITE }.timeout shouldBe Duration.INFINITE
        shouldThrow<ValidationException> {
            timeLimiterConfig { timeout = TimeLimiterConfig.HARD_TIMEOUT + 1.nanoseconds }
        }
        shouldThrow<ValidationException> { timeLimiterConfig { timeout = Duration.ZERO } }
        shouldThrow<ValidationException> { timeLimiterConfig { timeout = -Duration.INFINITE } }
    }

    test("a soft strategy holds from one to the hard ceiling of detached calls") {
        TimeoutStrategy.Soft(maxDetached = 1).maxDetached shouldBe 1
        val most = TimeoutStrategy.Soft(maxDetached = TimeoutStrategy.HARD_MAX_DETACHED)
        most.maxDetached shouldBe TimeoutStrategy.HARD_MAX_DETACHED
        shouldThrow<ValidationException> { TimeoutStrategy.Soft(maxDetached = 0) }
        shouldThrow<ValidationException> { TimeoutStrategy.Soft(maxDetached = TimeoutStrategy.HARD_MAX_DETACHED + 1) }
    }

    test("a soft strategy is a value") {
        val strategy = TimeoutStrategy.Soft()
        strategy.isCancellingOnTimeout shouldBe true
        strategy.maxDetached shouldBe TimeoutStrategy.DEFAULT_MAX_DETACHED
        strategy shouldBe TimeoutStrategy.Soft()
        strategy.hashCode() shouldBe TimeoutStrategy.Soft().hashCode()
        strategy.hashCode() shouldBe TimeoutStrategy.DEFAULT_MAX_DETACHED * HASH_FACTOR + true.hashCode()
        strategy shouldNotBe TimeoutStrategy.Soft(isCancellingOnTimeout = false)
        strategy shouldNotBe TimeoutStrategy.Soft(maxDetached = DETACHED)
        strategy shouldNotBe TimeoutStrategy.Hard
        TimeoutStrategy.Soft(isCancellingOnTimeout = false).toString() shouldBe
            "Soft(isCancellingOnTimeout=false, maxDetached=1000)"
    }

    test("a builder refuses changes once it was evaluated") {
        val builder = TimeLimiterConfigBuilder()
        builder.build()
        shouldThrow<IllegalStateException> { builder.timeout = 2.seconds }
        shouldThrow<IllegalStateException> { builder.strategy = TimeoutStrategy.Soft() }
    }
})
