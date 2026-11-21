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

package com.davils.kontinuity.cache

import com.davils.kontinuity.cache.fixture.CACHE
import com.davils.kontinuity.cache.fixture.withScope
import com.davils.kontinuity.testing.testEnvironment
import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

private const val DEFAULT_SIZE: Int = 1_000
private const val VIOLATIONS: Int = 6

@OptIn(ExperimentalKotest::class)
class CacheConfigTest : FunSpec({
    coroutineTestScope = true

    test("the defaults bound the cache to 1,000 entries without expiry") {
        val config = cacheConfig<String, String> {}
        config.maximumSize shouldBe DEFAULT_SIZE
        config.maximumWeight shouldBe null
        config.weigher shouldBe null
        config.eviction shouldBe Eviction.Lru
        config.expireAfterWrite shouldBe Duration.INFINITE
        config.expireAfterAccess shouldBe Duration.INFINITE
        config.expireAfter shouldBe null
        config.staleOnFailure shouldBe Duration.ZERO
        config.reapEvery shouldBe null
        config.isRecordingKeys shouldBe false
        config.toString() shouldBe "CacheConfig(maximumSize=1000, maximumWeight=none, eviction=Lru, " +
            "expireAfterWrite=Infinity, expireAfterAccess=Infinity, variableExpiry=false, staleOnFailure=0s, " +
            "reapEvery=none, isRecordingKeys=false)"
    }

    test("a weighed configuration holds at most the hard number of entries") {
        val config = cacheConfig<String, String> {
            maximumWeight = CacheConfig.HARD_MAXIMUM_WEIGHT
            weigher { _, value -> value.length }
            expireAfter { _, _ -> 1.minutes }
            reapEvery = 1.minutes
        }
        config.maximumSize shouldBe CacheConfig.HARD_MAXIMUM_SIZE
        config.toString() shouldBe "CacheConfig(maximumSize=10000000, maximumWeight=1000000000000, eviction=Lru, " +
            "expireAfterWrite=Infinity, expireAfterAccess=Infinity, variableExpiry=true, staleOnFailure=0s, " +
            "reapEvery=1m, isRecordingKeys=false)"
    }

    test("every bound accepts its limits") {
        cacheConfig<String, String> { maximumSize = 1 }.maximumSize shouldBe 1
        cacheConfig<String, String> { maximumSize = CacheConfig.HARD_MAXIMUM_SIZE }.maximumSize shouldBe
            CacheConfig.HARD_MAXIMUM_SIZE
        val lowest = cacheConfig<String, String> {
            maximumWeight = 1L
            weigher { _, _ -> 1 }
            expireAfterWrite = 1.nanoseconds
            expireAfterAccess = 1.nanoseconds
            reapEvery = 1.milliseconds
        }
        lowest.maximumWeight shouldBe 1L
        val highest = cacheConfig<String, String> {
            expireAfterWrite = CacheConfig.HARD_DURATION
            expireAfterAccess = CacheConfig.HARD_DURATION
            staleOnFailure = CacheConfig.HARD_DURATION
            reapEvery = CacheConfig.HARD_DURATION
        }
        highest.staleOnFailure shouldBe CacheConfig.HARD_DURATION
    }

    test("every bound refuses a value past its limits and the report names every problem") {
        val refusal = shouldThrow<ValidationException> {
            cacheConfig<String, String> {
                maximumSize = 0
                expireAfterWrite = Duration.ZERO
                expireAfterAccess = CacheConfig.HARD_DURATION + 1.nanoseconds
                staleOnFailure = -1.nanoseconds
                reapEvery = 1.milliseconds - 1.nanoseconds
                weigher { _, _ -> 1 }
            }
        }
        refusal.report.violations.size shouldBe VIOLATIONS
        shouldThrow<ValidationException> {
            cacheConfig<String, String> { maximumSize = CacheConfig.HARD_MAXIMUM_SIZE + 1 }
        }
        shouldThrow<ValidationException> {
            cacheConfig<String, String> { staleOnFailure = CacheConfig.HARD_DURATION * 2 }
        }
        shouldThrow<ValidationException> { cacheConfig<String, String> { reapEvery = CacheConfig.HARD_DURATION * 2 } }
        shouldThrow<ValidationException> {
            cacheConfig<String, String> {
                maximumWeight = 0L
                weigher { _, _ -> 1 }
            }
        }
        shouldThrow<ValidationException> {
            cacheConfig<String, String> {
                maximumWeight = CacheConfig.HARD_MAXIMUM_WEIGHT + 1L
                weigher { _, _ -> 1 }
            }
        }
    }

    test("a weight needs a weigher and excludes a maximum size") {
        shouldThrow<ValidationException> { cacheConfig<String, String> { maximumWeight = 1L } }
        shouldThrow<ValidationException> {
            cacheConfig<String, String> {
                maximumSize = 1
                maximumWeight = 1L
                weigher { _, _ -> 1 }
            }
        }
    }

    test("a reaper needs the scope of the environment") {
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        shouldThrow<IllegalArgumentException> {
            cache<String, String>(CACHE, environment) { reapEvery = 1.minutes }
        }
        withScope { scope ->
            val scoped = testEnvironment(testCoroutineScheduler.timeSource, scope)
            val config = cacheConfig<String, String> { reapEvery = 1.minutes }
            cache(CACHE, config, scoped).config shouldBe config
            cache(CACHE, cacheConfig<String, String> {}).size shouldBe 0
        }
    }

    test("an evaluated builder refuses changes") {
        val builder = CacheBuilder<String, String>()
        builder.build()
        shouldThrow<IllegalStateException> { builder.maximumSize = 1 }
        shouldThrow<IllegalStateException> { builder.weigher { _, _ -> 1 } }
        shouldThrow<IllegalStateException> { builder.expireAfter { _, _ -> 1.minutes } }
    }

    test("a cache shows its name and size and is an instance of the cache kind") {
        val cache = cache<String, String>(CACHE)
        cache.put(key = "a", value = "b")
        cache.toString() shouldBe "Cache(users, size=1)"
        cache.kind.id shouldBe "cache"
        cache.name.value shouldBe CACHE
    }
})
