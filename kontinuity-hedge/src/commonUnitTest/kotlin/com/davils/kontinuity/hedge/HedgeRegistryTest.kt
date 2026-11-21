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

import com.davils.kontinuity.core.registry.InstanceLimitException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

private const val SEARCH: String = "search"
private const val ORDERS: String = "orders"
private const val TAIL: String = "tail"
private const val TAIL_ATTEMPTS: Int = 3
private const val DEFAULT_ATTEMPTS: Int = 2
private const val CUSTOM_ATTEMPTS: Int = 4

class HedgeRegistryTest : FunSpec({
    test("a registry creates hedges from its configurations once per name") {
        val registry = hedgeRegistry {
            defaults { maxHedgedAttempts = DEFAULT_ATTEMPTS }
            config(TAIL) { maxHedgedAttempts = TAIL_ATTEMPTS }
        }
        val search = registry.hedge(SEARCH)
        search.config.maxHedgedAttempts shouldBe DEFAULT_ATTEMPTS
        registry.hedge(name = SEARCH, config = TAIL) shouldBeSameInstanceAs search
        registry.hedge(name = ORDERS, config = TAIL).config.maxHedgedAttempts shouldBe TAIL_ATTEMPTS
        registry.all.map { hedge -> hedge.name.value } shouldBe listOf(SEARCH, ORDERS)
        registry.toString() shouldBe "HedgeRegistry(2 hedges)"
    }

    test("a registry creates a hedge from a configuration or a block") {
        val registry = hedgeRegistry { config(TAIL) { maxHedgedAttempts = TAIL_ATTEMPTS } }
        val custom = registry.hedge(SEARCH, base = TAIL) { failures { ignore<IllegalStateException>() } }
        custom.config.maxHedgedAttempts shouldBe TAIL_ATTEMPTS
        custom.config.failures.rules shouldBe 1
        registry.hedge(SEARCH) { maxHedgedAttempts = CUSTOM_ATTEMPTS } shouldBeSameInstanceAs custom
        val given = registry.hedge(ORDERS, hedgeConfig { maxHedgedAttempts = CUSTOM_ATTEMPTS })
        given.config.maxHedgedAttempts shouldBe CUSTOM_ATTEMPTS
        shouldThrow<IllegalArgumentException> { registry.hedge("other", base = "missing") {} }
    }

    test("a full registry refuses new hedges") {
        val registry = hedgeRegistry { limits { maximumInstances = 1 } }
        registry.hedge(SEARCH)
        shouldThrow<InstanceLimitException> { registry.hedge(ORDERS) }
    }

    test("a hedge from a configuration built before keeps it") {
        val config = hedgeConfig { maxHedgedAttempts = CUSTOM_ATTEMPTS }
        hedge(SEARCH, config).config shouldBeSameInstanceAs config
        hedgeRegistry().environment.scope shouldBe null
        shouldThrow<IllegalArgumentException> { hedge("not a name!") }
    }
})
