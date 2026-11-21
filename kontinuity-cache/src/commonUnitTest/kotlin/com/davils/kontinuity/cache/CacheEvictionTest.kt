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

import com.davils.konfetti.testing.SeededEntropy
import com.davils.kontinuity.cache.fixture.ReferenceModel
import com.davils.kontinuity.core.Environment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

private const val KEYS: Int = 12
private const val FIRST: Int = 1
private const val SECOND: Int = 2
private const val THIRD: Int = 3
private const val FOURTH: Int = 4
private const val CAPACITY: Int = 5
private const val OPERATIONS: Int = 3_000
private const val READ_SHARE: Long = 2L
private const val CHOICES: Long = 3L
private const val SEEDS: Int = 4
private const val HEAVY: Int = 7
private const val LIGHT: Int = 2
private const val WEIGHT_BOUND: Long = 10L

class CacheEvictionTest : FunSpec({
    Eviction.entries.forEach { eviction ->
        test("$eviction evicts like its reference model under random reads and writes") {
            repeat(SEEDS) { seed ->
                val entropy = SeededEntropy(seed.toLong())
                val cache = cache<Int, Int>("model", Environment.Default) {
                    maximumSize = CAPACITY
                    this.eviction = eviction
                }
                val model = ReferenceModel(CAPACITY, eviction)
                repeat(OPERATIONS) { step ->
                    val key = (entropy.nextLong() ushr 1).mod(KEYS.toLong()).toInt()
                    val isRead = (entropy.nextLong() ushr 1).mod(CHOICES) < READ_SHARE
                    if (isRead) (cache[key] != null) shouldBe model.get(key)
                    if (!isRead) cache.put(key = key, value = step)
                    if (!isRead) model.put(key)
                    val held = (0 until KEYS).filter { candidate -> candidate in cache }
                    held shouldContainExactlyInAnyOrder model.keys
                }
                cache.size shouldBe CAPACITY
            }
        }
    }

    test("a full LRU cache evicts the entry used longest ago") {
        val cache = cache<String, Int>("lru") { maximumSize = 2 }
        cache.put(key = "a", value = FIRST)
        cache.put(key = "b", value = SECOND)
        cache["a"]
        cache.put(key = "c", value = THIRD)
        cache.peek("b") shouldBe null
        cache.peek("a") shouldBe FIRST
    }

    test("a full LFU cache evicts the entry used least often and never the entry it writes") {
        val cache = cache<String, Int>("lfu") {
            maximumSize = 2
            eviction = Eviction.Lfu
        }
        cache.put(key = "a", value = FIRST)
        cache.put(key = "b", value = SECOND)
        cache["a"]
        cache["b"]
        cache["b"]
        cache.put(key = "c", value = THIRD)
        cache.peek("a") shouldBe null
        cache.put(key = "d", value = FOURTH)
        cache.peek("c") shouldBe null
        cache.peek("d") shouldBe FOURTH
        cache.peek("b") shouldBe SECOND
    }

    test("a full FIFO cache evicts the entry written first, whatever was read") {
        val cache = cache<String, Int>("fifo") {
            maximumSize = 2
            eviction = Eviction.Fifo
        }
        cache.put(key = "a", value = FIRST)
        cache.put(key = "b", value = SECOND)
        cache["a"]
        cache.put(key = "c", value = THIRD)
        cache.peek("a") shouldBe null
        cache.put(key = "b", value = SECOND)
        cache.put(key = "d", value = FOURTH)
        cache.peek("c") shouldBe null
        cache.peek("b") shouldBe SECOND
    }

    test("a weighed cache evicts until the weight fits and refuses an entry heavier than its bound") {
        val cache = cache<String, Int>("weighed") {
            maximumWeight = WEIGHT_BOUND
            weigher { _, value -> value }
        }
        cache.put(key = "a", value = LIGHT)
        cache.put(key = "b", value = LIGHT)
        cache.put(key = "c", value = HEAVY)
        cache.peek("a") shouldBe null
        cache.peek("b") shouldBe LIGHT
        cache.snapshot().weight shouldBe (LIGHT + HEAVY).toLong()
        cache.put(key = "c", value = LIGHT)
        cache.snapshot().weight shouldBe (LIGHT + LIGHT).toLong()
        cache.put(key = "c", value = WEIGHT_BOUND.toInt() + 1)
        cache.peek("c") shouldBe null
        cache.snapshot().weight shouldBe LIGHT.toLong()
        cache.snapshot().evictions shouldBe 2L
    }

    test("an entry of weight zero is held without weight and an unweighed entry weighs one") {
        val weighed = cache<String, Int>("zero") {
            maximumWeight = 1L
            weigher { _, _ -> 0 }
        }
        repeat(CAPACITY) { index -> weighed.put(key = "k$index", value = index) }
        weighed.size shouldBe CAPACITY
        weighed.snapshot().weight shouldBe 0L
        val counted = cache<String, Int>("counted")
        repeat(CAPACITY) { index -> counted.put(key = "k$index", value = index) }
        counted.snapshot().weight shouldBe CAPACITY.toLong()
    }

    test("a failing or negative weigher fails the write and leaves the cache unchanged") {
        val cache = cache<String, Int>("weigher") {
            maximumWeight = WEIGHT_BOUND
            weigher { _, value ->
                check(value != HEAVY) { "unweighable" }
                -value
            }
        }
        val failed = shouldThrow<IllegalArgumentException> { cache.put(key = "a", value = HEAVY) }
        (failed.cause is IllegalStateException) shouldBe true
        shouldThrow<IllegalArgumentException> { cache.put(key = "a", value = LIGHT) }
        cache.size shouldBe 0
    }
})
