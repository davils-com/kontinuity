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
import com.davils.kontinuity.cache.fixture.OTHER_KEY
import com.davils.kontinuity.cache.fixture.Probe
import com.davils.kontinuity.cache.fixture.VALUE
import com.davils.kontinuity.cache.fixture.smallCache
import com.davils.kontinuity.cache.fixture.withScope
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val SHORT: String = "short"
private const val HUGE: String = "huge"
private const val LONG: String = "long"
private const val ENDLESS: String = "endless"
private const val BROKEN: String = "broken"
private const val NEGATIVE: String = "negative"
private const val EXPIRING: Int = 300
private const val LIVING: Int = 300
private const val FIRST_CHUNK: Int = 256
private const val MANY: Int = 1_000
private val LIFETIME = 10.seconds
private val ALMOST = 9.seconds
private val GRACE = 30.seconds
private val LIFETIMES = mapOf(
    SHORT to LIFETIME,
    ENDLESS to Duration.INFINITE,
    HUGE to 1_000.days,
    NEGATIVE to -LIFETIME,
)

@OptIn(ExperimentalKotest::class)
class CacheExpiryTest : FunSpec({
    coroutineTestScope = true

    test("an entry expires after its write and a lookup removes it") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) { expireAfterWrite = LIFETIME }
        cache.put(key = KEY, value = VALUE)
        delay(ALMOST)
        cache[KEY] shouldBe VALUE
        delay(1.seconds)
        cache.size shouldBe 1
        (KEY in cache) shouldBe false
        cache.peek(KEY) shouldBe null
        cache.size shouldBe 1
        cache[KEY] shouldBe null
        cache.size shouldBe 0
        cache.snapshot().expirations shouldBe 1L
    }

    test("a read renews the access expiry and a peek does not") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) { expireAfterAccess = LIFETIME }
        cache.put(key = KEY, value = VALUE)
        cache.put(key = OTHER_KEY, value = VALUE)
        delay(ALMOST)
        cache[KEY] shouldBe VALUE
        cache.peek(OTHER_KEY) shouldBe VALUE
        delay(ALMOST)
        cache[KEY] shouldBe VALUE
        cache[OTHER_KEY] shouldBe null
    }

    test("the write expiry ends an entry whose access expiry is renewed") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) {
            expireAfterWrite = LIFETIME
            expireAfterAccess = ALMOST
        }
        cache.put(key = KEY, value = VALUE)
        delay(ALMOST - 1.seconds)
        cache[KEY] shouldBe VALUE
        delay(1.seconds)
        cache[KEY] shouldBe VALUE
        delay(1.seconds)
        cache[KEY] shouldBe null
    }

    test("an LFU cache sweeps every bucket and keeps its buckets through removals and clears") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) {
            eviction = Eviction.Lfu
            expireAfterWrite = LIFETIME
        }
        cache.cleanUp() shouldBe 0
        cache.put(key = KEY, value = VALUE)
        cache.put(key = OTHER_KEY, value = VALUE)
        cache.put(key = SHORT, value = VALUE)
        cache[KEY]
        cache[KEY]
        cache[OTHER_KEY]
        cache.remove(KEY) shouldBe VALUE
        delay(LIFETIME)
        cache.cleanUp() shouldBe 2
        cache.put(key = KEY, value = VALUE)
        cache.clear()
        cache.put(key = KEY, value = VALUE)
        cache[KEY] shouldBe VALUE
        cache.size shouldBe 1
    }

    test("the variable lifetime shortens the write expiry and is clamped") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) {
            expireAfterWrite = 1.minutes
            expireAfter { key, _ ->
                check(key != BROKEN) { "no lifetime" }
                LIFETIMES[key] ?: 2.minutes
            }
        }
        listOf(SHORT, LONG, ENDLESS, HUGE, NEGATIVE, BROKEN).forEach { key -> cache.put(key = key, value = VALUE) }
        cache.size shouldBe 2 + 2
        cache.snapshot().expirations shouldBe 2L
        delay(LIFETIME)
        cache[SHORT] shouldBe null
        cache[LONG] shouldBe VALUE
        delay(1.minutes)
        cache[LONG] shouldBe null
        cache[ENDLESS] shouldBe null
    }

    test("an endless variable lifetime and a clamped one keep entries without a fixed expiry") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) {
            expireAfter { key, _ -> LIFETIMES[key] ?: Duration.INFINITE }
        }
        cache.put(key = KEY, value = VALUE)
        cache.put(key = HUGE, value = VALUE)
        delay(CacheConfig.HARD_DURATION - 1.seconds)
        cache[HUGE] shouldBe VALUE
        delay(1.seconds)
        cache[HUGE] shouldBe null
        cache[KEY] shouldBe VALUE
    }

    test("the size counts expired entries until a clean-up removes them") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) {
            maximumSize = MANY * 2
            expireAfterWrite = LIFETIME
        }
        repeat(MANY) { index -> cache.put(key = "old$index", value = VALUE) }
        delay(LIFETIME)
        cache.put(key = KEY, value = VALUE)
        cache.size shouldBe MANY + 1
        cache.cleanUp() shouldBe MANY
        cache.size shouldBe 1
        cache.cleanUp() shouldBe 0
        cache.snapshot().expirations shouldBe MANY.toLong()
    }

    test("a clean-up keeps the stale entries within their grace") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) {
            expireAfterWrite = LIFETIME
            staleOnFailure = GRACE
        }
        cache.put(key = KEY, value = VALUE)
        delay(LIFETIME)
        cache[KEY] shouldBe null
        cache.cleanUp() shouldBe 0
        cache.size shouldBe 1
        delay(GRACE)
        cache.cleanUp() shouldBe 1
    }

    test("the reaper removes expired entries on the scope of the environment") {
        withScope { scope ->
            val environment = testEnvironment(testCoroutineScheduler.timeSource, scope)
            val cache = smallCache(environment) {
                expireAfterWrite = LIFETIME
                reapEvery = 1.minutes
            }
            cache.put(key = KEY, value = VALUE)
            delay(1.minutes + 1.seconds)
            cache.size shouldBe 0
            cache.snapshot().expirations shouldBe 1L
        }
    }

    test("a clean-up continues past the entries other calls remove or move between its chunks") {
        val probes = cache<Probe, Int>("probes", testEnvironment(testCoroutineScheduler.timeSource)) {
            maximumSize = MANY
            expireAfterWrite = LIFETIME
            isRecordingKeys = true
        }
        var isShown = false
        val onShow = {
            if (!isShown) {
                isShown = true
                probes.remove(Probe(FIRST_CHUNK))
                probes[Probe(FIRST_CHUNK).next()]
                probes.cleanUp()
            }
        }
        val log = recordEvents(probes.events)
        repeat(EXPIRING) { index -> probes.put(key = Probe(index, onShow), value = index) }
        delay(LIFETIME)
        repeat(LIVING) { index -> probes.put(key = Probe(EXPIRING + index), value = index) }
        probes.cleanUp() shouldBe FIRST_CHUNK
        probes.size shouldBe LIVING
        probes.snapshot().expirations shouldBe (EXPIRING - 1).toLong()
        log.stop()
    }

    test("a clear between the chunks of a clean-up ends it") {
        val probes = cache<Probe, Int>("cleared", testEnvironment(testCoroutineScheduler.timeSource)) {
            maximumSize = MANY
            expireAfterWrite = LIFETIME
            isRecordingKeys = true
        }
        val log = recordEvents(probes.events)
        repeat(EXPIRING) { index -> probes.put(key = Probe(index) { probes.clear() }, value = index) }
        delay(LIFETIME)
        probes.cleanUp() shouldBe FIRST_CHUNK
        probes.size shouldBe 0
        log.stop()
    }
})
