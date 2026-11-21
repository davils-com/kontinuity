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

import com.davils.kontinuity.cache.fixture.KEY
import com.davils.kontinuity.cache.fixture.NEWER_VALUE
import com.davils.kontinuity.cache.fixture.OTHER_KEY
import com.davils.kontinuity.cache.fixture.VALUE
import com.davils.kontinuity.cache.fixture.smallCache
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private val LIFETIME = 10.seconds

@OptIn(ExperimentalKotest::class)
class CacheOperationsTest : FunSpec({
    coroutineTestScope = true

    test("a put replaces the value and a removal returns it") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        cache.put(key = KEY, value = VALUE)
        cache.put(key = KEY, value = NEWER_VALUE)
        cache[KEY] shouldBe NEWER_VALUE
        cache.size shouldBe 1
        cache.remove(KEY) shouldBe NEWER_VALUE
        cache.remove(KEY) shouldBe null
        cache.size shouldBe 0
    }

    test("a put-if-absent keeps a live value and replaces an expired one") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) { expireAfterWrite = LIFETIME }
        cache.putIfAbsent(key = KEY, value = VALUE) shouldBe null
        cache.putIfAbsent(key = KEY, value = NEWER_VALUE) shouldBe VALUE
        cache[KEY] shouldBe VALUE
        delay(LIFETIME)
        cache.putIfAbsent(key = KEY, value = NEWER_VALUE) shouldBe null
        cache[KEY] shouldBe NEWER_VALUE
    }

    test("a removal of an expired entry removes it and returns nothing") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) { expireAfterWrite = LIFETIME }
        cache.put(key = KEY, value = VALUE)
        delay(LIFETIME)
        cache.remove(KEY) shouldBe null
        cache.size shouldBe 0
    }

    test("peek and contains change nothing") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        cache.put(key = KEY, value = VALUE)
        cache.peek(KEY) shouldBe VALUE
        cache.peek(OTHER_KEY) shouldBe null
        (KEY in cache) shouldBe true
        (OTHER_KEY in cache) shouldBe false
        cache.snapshot().hits shouldBe 0L
        cache.snapshot().misses shouldBe 0L
    }

    test("a clear removes every entry and keeps the counts") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        cache.put(key = KEY, value = VALUE)
        cache.put(key = OTHER_KEY, value = VALUE)
        cache[KEY]
        cache.clear()
        cache.size shouldBe 0
        cache.snapshot().hits shouldBe 1L
        cache.snapshot().weight shouldBe 0L
        cache.put(key = KEY, value = VALUE)
        cache[KEY] shouldBe VALUE
    }

    test("a cache holds exactly its maximum size and evicts past it") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) { maximumSize = 1 }
        cache.put(key = KEY, value = VALUE)
        cache.size shouldBe 1
        cache.put(key = OTHER_KEY, value = VALUE)
        cache.size shouldBe 1
        cache.peek(KEY) shouldBe null
        cache.snapshot().evictions shouldBe 1L
    }

    test("a cache bounded by the hard size is created without allocating it") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) {
            maximumSize = CacheConfig.HARD_MAXIMUM_SIZE
        }
        cache.put(key = KEY, value = VALUE)
        cache.config.maximumSize shouldBe CacheConfig.HARD_MAXIMUM_SIZE
    }
})
