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

import com.davils.kollect.core.meterRegistry
import com.davils.konfetti.testing.EventRecorder
import com.davils.konfetti.testing.testEventBus
import com.davils.kontinuity.cache.fixture.Gate
import com.davils.kontinuity.cache.fixture.KEY
import com.davils.kontinuity.cache.fixture.LOADED_VALUE
import com.davils.kontinuity.cache.fixture.OTHER_KEY
import com.davils.kontinuity.cache.fixture.Probe
import com.davils.kontinuity.cache.fixture.VALUE
import com.davils.kontinuity.cache.fixture.gauge
import com.davils.kontinuity.cache.fixture.quietly
import com.davils.kontinuity.cache.fixture.recorded
import com.davils.kontinuity.cache.fixture.smallCache
import com.davils.kontinuity.cache.fixture.sum
import com.davils.kontinuity.cache.fixture.withScope
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import com.davils.kore.testing.HostileFragments
import com.davils.kore.testing.isSafeToShow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val NAME: String = "users"
private const val GETS: String = "kontinuity.cache.gets"
private const val LOADS: String = "kontinuity.cache.loads"
private const val LOAD_FAILED: String = "load failed"
private const val MISSES: Double = 3.0
private const val HALF: Double = 0.5
private const val CALLS: Int = 400
private const val KEY_SPACE: Int = 7
private const val LOADING_EVERY: Int = 3
private const val MANY_KEYS: Int = 100
private const val SHOWN_KEYS: Int = 64
private val LIFETIME = 10.seconds

@OptIn(ExperimentalKotest::class)
class CacheTelemetryTest : FunSpec({
    coroutineTestScope = true

    test("the events tell every lookup, load, eviction, expiry, stale value and clear without keys") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) {
            maximumSize = 2
            expireAfterWrite = LIFETIME
            staleOnFailure = LIFETIME
        }
        val log = recordEvents(cache.events)
        cache[KEY]
        cache.getOrLoad(KEY) { LOADED_VALUE }
        cache[KEY]
        quietly { cache.getOrLoad(OTHER_KEY) { error(LOAD_FAILED) } }
        cache.put(key = OTHER_KEY, value = VALUE)
        cache.put(key = "third", value = VALUE)
        delay(LIFETIME)
        cache.getOrLoad(OTHER_KEY) { error("reload failed") } shouldBe VALUE
        delay(LIFETIME)
        cache.cleanUp()
        cache.put(key = KEY, value = VALUE)
        cache.clear()
        testCoroutineScheduler.runCurrent()
        log.stop()
        val shown = log.events.map(CacheEvent::toString)
        shown shouldContain "Miss(users)"
        shown shouldContain "Loaded(users, 0s)"
        shown shouldContain "Hit(users)"
        shown shouldContain "LoadFailed(users, IllegalStateException)"
        shown shouldContain "Evicted(users, size, 1)"
        shown shouldContain "StaleServed(users)"
        shown shouldContain "Expired(users, 2)"
        shown shouldContain "Cleared(users, 1)"
        log.events.forEach { event -> event.kind shouldBe ResilienceKind.Cache }
        log.events.forEach { event -> event.instance.value shouldBe NAME }
    }

    test("a cache that records keys shows them escaped and never trusts their toString") {
        val cache = cache<Any, String>(NAME, testEnvironment(testCoroutineScheduler.timeSource)) {
            maximumSize = 1
            isRecordingKeys = true
        }
        val log = recordEvents(cache.events)
        HostileFragments.all.forEach { fragment -> cache.put(key = "key$fragment", value = VALUE) }
        cache[Probe(1) { error("broken toString") }]
        cache.getOrLoad(KEY) { LOADED_VALUE }
        quietly { cache.getOrLoad(OTHER_KEY) { error(LOAD_FAILED) } }
        testCoroutineScheduler.runCurrent()
        log.stop()
        val shown = log.events.map(CacheEvent::toString)
        shown shouldContain "Miss(users, key=<unshowable>)"
        shown shouldContain "Loaded(users, key=alice, 0s)"
        shown shouldContain "LoadFailed(users, key=bob, IllegalStateException)"
        shown.forEach { text -> isSafeToShow(text) shouldBe true }
        val evicted = log.only<CacheEvent.Evicted>()
        evicted.size shouldBe HostileFragments.all.size
        evicted.forEach { event -> event.keys.size shouldBe 1 }
        evicted.forEach { event -> isSafeToShow(event.keys.single()) shouldBe true }
    }

    test("a failure without a simple name is shown as a throwable") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        val log = recordEvents(cache.events)
        quietly { cache.getOrLoad(KEY) { throw object : IllegalStateException(LOAD_FAILED) {} } }
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.events.map(CacheEvent::toString) shouldContain "LoadFailed(users, Throwable)"
    }

    test("an event shows at most 64 keys but counts every entry") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) {
            maximumSize = MANY_KEYS
            expireAfterWrite = LIFETIME
            isRecordingKeys = true
        }
        val log = recordEvents(cache.events)
        repeat(MANY_KEYS) { index -> cache.put(key = "k$index", value = VALUE) }
        delay(LIFETIME)
        cache.cleanUp()
        testCoroutineScheduler.runCurrent()
        log.stop()
        val expired = log.only<CacheEvent.Expired>().single()
        expired.count shouldBe MANY_KEYS
        expired.keys.size shouldBe SHOWN_KEYS
        expired.toString().startsWith("Expired(users, 100, keys=[k0, k1") shouldBe true
    }

    test("the meters count lookups, loads and evictions and show the size and the weight") {
        val registry = meterRegistry()
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource) { meters(registry) }) {
            maximumWeight = 2L
            weigher { _, value -> value.length / VALUE.length }
        }
        cache[KEY]
        cache.getOrLoad(KEY) { VALUE }
        cache[KEY]
        quietly { cache.getOrLoad(OTHER_KEY) { error(LOAD_FAILED) } }
        cache.put(key = OTHER_KEY, value = VALUE + VALUE)
        cache.put(key = "huge", value = VALUE + VALUE + VALUE)
        registry.sum(GETS, NAME, "hit") shouldBe 1.0
        registry.sum(GETS, NAME, "miss") shouldBe MISSES
        registry.sum(LOADS, NAME, "success") shouldBe 1.0
        registry.sum(LOADS, NAME, "failure") shouldBe 1.0
        registry.sum("kontinuity.cache.evictions", NAME, "weight") shouldBe 2.0
        registry.recorded("kontinuity.cache.load.duration", NAME) shouldBe 2L
        registry.gauge("kontinuity.cache.size", NAME) shouldBe 1.0
        registry.gauge("kontinuity.cache.weight", NAME) shouldBe 2.0
        cache.clear()
        registry.gauge("kontinuity.cache.size", NAME) shouldBe 0.0
    }

    test("the events reach the topic of the event bus") {
        withScope { scope ->
            val bus = testEventBus(scope)
            val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource) { events(bus) })
            val recorder = EventRecorder<CacheEvent>()
            bus[CacheEvent.TOPIC].consume("recorder") { handle(recorder::record) }
            cache[KEY]
            recorder.awaitCount(1)
            recorder.payloads.single().toString() shouldBe "Miss(users)"
        }
    }

    test("every lookup counts exactly once as a hit or a miss") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) { maximumSize = KEY_SPACE / 2 }
        val gate = Gate<String>()
        val calls = List(CALLS) { index ->
            val key = "k${index % KEY_SPACE}"
            async {
                if (index % LOADING_EVERY == 0) cache[key]
                if (index % LOADING_EVERY != 0) cache.getOrLoad(key) { gate.pass() }
            }
        }
        testCoroutineScheduler.runCurrent()
        gate.open(VALUE)
        calls.forEach { call -> call.await() }
        val snapshot = cache.snapshot()
        snapshot.hits + snapshot.misses shouldBe CALLS.toLong()
        snapshot.loads + snapshot.loadFailures shouldBe gate.entered.toLong()
    }

    test("a snapshot is a value and its hit ratio is zero before the first lookup") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        val empty = cache.snapshot()
        empty.hitRatio shouldBe 0.0
        empty shouldBe cache.snapshot()
        empty.hashCode() shouldBe cache.snapshot().hashCode()
        cache.put(key = KEY, value = VALUE)
        cache[KEY]
        cache[OTHER_KEY]
        val counted = cache.snapshot()
        counted shouldNotBe empty
        counted.hitRatio shouldBe HALF
        val other: Any = VALUE
        (counted == other) shouldBe false
        counted.toString() shouldBe "CacheSnapshot(size=1, weight=1, hits=1, misses=1, loads=0, loadFailures=0, " +
            "evictions=0, expirations=0, staleServed=0, hitRatio=0.5)"
    }
})
