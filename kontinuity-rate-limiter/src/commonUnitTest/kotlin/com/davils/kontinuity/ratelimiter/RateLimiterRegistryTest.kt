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

import com.davils.kontinuity.core.registry.InstanceLimitException
import com.davils.kontinuity.core.through
import com.davils.kontinuity.ratelimiter.fixture.LIMIT
import com.davils.kontinuity.ratelimiter.fixture.PERIOD
import com.davils.kontinuity.ratelimiter.fixture.smallLimiter
import com.davils.kontinuity.testing.testEnvironment
import com.davils.kore.testing.HostileFragments
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

private const val SEARCH: String = "search"
private const val ORDERS: String = "orders"
private const val STRICT: String = "strict"
private const val STRICT_LIMIT: Int = 5
private const val DEFAULT_LIMIT: Int = 20
private const val CUSTOM_LIMIT: Int = 7
private const val ELEMENTS: Int = 7

@OptIn(ExperimentalKotest::class)
class RateLimiterRegistryTest : FunSpec({
    coroutineTestScope = true

    test("a registry creates limiters from its configurations once per name") {
        val registry = rateLimiterRegistry {
            defaults { limitForPeriod = DEFAULT_LIMIT }
            config(STRICT) { limitForPeriod = STRICT_LIMIT }
        }
        val search = registry.rateLimiter(SEARCH)
        search.config.limitForPeriod shouldBe DEFAULT_LIMIT
        registry.rateLimiter(name = SEARCH, config = STRICT) shouldBeSameInstanceAs search
        registry.rateLimiter(name = ORDERS, config = STRICT).limitForPeriod shouldBe STRICT_LIMIT
        registry.all.map { limiter -> limiter.name.value } shouldBe listOf(SEARCH, ORDERS)
        registry.toString() shouldBe "RateLimiterRegistry(2 rate limiters)"
    }

    test("a registry creates a limiter from a configuration or a block") {
        val registry = rateLimiterRegistry { config(STRICT) { limitForPeriod = STRICT_LIMIT } }
        val custom = registry.rateLimiter(SEARCH, base = STRICT) { algorithm = RateLimiterAlgorithm.SlidingWindow }
        custom.config.limitForPeriod shouldBe STRICT_LIMIT
        custom.config.algorithm shouldBe RateLimiterAlgorithm.SlidingWindow
        registry.rateLimiter(SEARCH) { limitForPeriod = CUSTOM_LIMIT } shouldBeSameInstanceAs custom
        val given = registry.rateLimiter(ORDERS, rateLimiterConfig { limitForPeriod = CUSTOM_LIMIT })
        given.limitForPeriod shouldBe CUSTOM_LIMIT
        shouldThrow<IllegalArgumentException> { registry.rateLimiter("other", base = "missing") {} }
        registry.environment.scope shouldBe null
    }

    test("a full registry refuses new limiters") {
        val registry = rateLimiterRegistry { limits { maximumInstances = 1 } }
        registry.rateLimiter(SEARCH)
        shouldThrow<InstanceLimitException> { registry.rateLimiter(ORDERS) }
    }

    test("a limiter refuses every hostile name") {
        val refused = HostileFragments.all.count { fragment ->
            runCatching {
                rateLimiter(
                    "search$fragment",
                )
            }.isFailure
        }
        refused shouldBe HostileFragments.all.size - 2
    }

    test("a limiter from a configuration built before keeps it") {
        val config = rateLimiterConfig { limitForPeriod = CUSTOM_LIMIT }
        rateLimiter(SEARCH, config).config shouldBeSameInstanceAs config
        rateLimiterRegistry().all shouldBe emptyList()
    }

    test("a limited flow takes permits for every element") {
        val timeSource = testCoroutineScheduler.timeSource
        val limiter = smallLimiter(testEnvironment(timeSource))
        val start = timeSource.markNow()
        val elements = (1..ELEMENTS).asFlow()
        elements.rateLimiter(limiter).toList() shouldBe (1..ELEMENTS).toList()
        start.elapsedNow() shouldBe PERIOD * (ELEMENTS / LIMIT)
        limiter.snapshot().acquired shouldBe ELEMENTS.toLong()
    }

    test("a flow through a limiter takes one permit for the whole collection") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        val elements = (1..ELEMENTS).asFlow()
        elements.through(limiter).toList() shouldBe (1..ELEMENTS).toList()
        limiter.snapshot().permitsGranted shouldBe 1L
    }
})
