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

import com.davils.kontinuity.cache.fixture.Gate
import com.davils.kontinuity.cache.fixture.KEY
import com.davils.kontinuity.cache.fixture.LOADED_VALUE
import com.davils.kontinuity.cache.fixture.NEWER_VALUE
import com.davils.kontinuity.cache.fixture.VALUE
import com.davils.kontinuity.cache.fixture.failureOf
import com.davils.kontinuity.cache.fixture.smallCache
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val LOAD_FAILED: String = "load failed"
private const val CALLERS: Int = 50
private const val WAITERS: Int = 3
private val LIFETIME = 10.seconds
private val GRACE = 30.seconds

@OptIn(ExperimentalKotest::class)
class CacheLoadTest : FunSpec({
    coroutineTestScope = true

    test("concurrent misses of one key share exactly one load") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        val gate = Gate<String>()
        val calls = List(CALLERS) { async { cache.getOrLoad(KEY) { gate.pass() } } }
        testCoroutineScheduler.runCurrent()
        gate.entered shouldBe 1
        gate.open(LOADED_VALUE)
        calls.map { call -> call.await() }.toSet() shouldBe setOf(LOADED_VALUE)
        cache[KEY] shouldBe LOADED_VALUE
        val snapshot = cache.snapshot()
        snapshot.loads shouldBe 1L
        snapshot.misses shouldBe CALLERS.toLong()
        snapshot.hits shouldBe 1L
    }

    test("a hit returns the value without running the loader") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        cache.put(key = KEY, value = VALUE)
        cache.getOrLoad(KEY) { error("never loaded") } shouldBe VALUE
        cache.snapshot().hits shouldBe 1L
    }

    test("a failed load fails the loading caller with its own failure and every waiter with an exception of its own") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        val gate = Gate<Unit>()
        val failure = IllegalStateException(LOAD_FAILED)
        val loader: suspend (String) -> String = {
            gate.pass()
            throw failure
        }
        val calls = List(WAITERS + 1) { async { failureOf { cache.getOrLoad(KEY, loader) } } }
        testCoroutineScheduler.runCurrent()
        gate.open(Unit)
        val failures = calls.map { call -> call.await() }
        failures.first() shouldBeSameInstanceAs failure
        val shared = failures.drop(1).map { waiter -> waiter.shouldBeInstanceOf<CacheLoadException>() }
        shared.forEach { waiter -> waiter.cause shouldBeSameInstanceAs failure }
        shared.forEach { waiter -> waiter.instance.value shouldBe "users" }
        shared.map { waiter -> waiter.hashCode() }.toSet().size shouldBe WAITERS
        shared.first().message shouldBe "The shared load of the cache users failed"
        cache.snapshot().loadFailures shouldBe 1L
        cache.peek(KEY) shouldBe null
    }

    test("a cancelled loading caller lets a waiter load again") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        val gate = Gate<String>()
        val leader = async { cache.getOrLoad(KEY) { gate.pass() } }
        val waiter = async { cache.getOrLoad(KEY) { gate.pass() } }
        testCoroutineScheduler.runCurrent()
        leader.cancel()
        testCoroutineScheduler.runCurrent()
        gate.entered shouldBe 2
        gate.open(LOADED_VALUE)
        waiter.await() shouldBe LOADED_VALUE
        val snapshot = cache.snapshot()
        snapshot.loads shouldBe 1L
        snapshot.loadFailures shouldBe 0L
        snapshot.misses shouldBe 2L
    }

    test("a cancelled waiter leaves the load running") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        val gate = Gate<String>()
        val leader = async { cache.getOrLoad(KEY) { gate.pass() } }
        val waiter = async { cache.getOrLoad(KEY) { gate.pass() } }
        testCoroutineScheduler.runCurrent()
        waiter.cancel()
        gate.open(LOADED_VALUE)
        leader.await() shouldBe LOADED_VALUE
        gate.entered shouldBe 1
    }

    test("a write during a load keeps the newer value") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        val gate = Gate<String>()
        val leader = async { cache.getOrLoad(KEY) { gate.pass() } }
        testCoroutineScheduler.runCurrent()
        cache.put(key = KEY, value = NEWER_VALUE)
        gate.open(LOADED_VALUE)
        leader.await() shouldBe LOADED_VALUE
        cache[KEY] shouldBe NEWER_VALUE
    }

    test("a put-if-absent during a load keeps its value") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        val gate = Gate<String>()
        val leader = async { cache.getOrLoad(KEY) { gate.pass() } }
        testCoroutineScheduler.runCurrent()
        cache.putIfAbsent(key = KEY, value = NEWER_VALUE) shouldBe null
        gate.open(LOADED_VALUE)
        leader.await() shouldBe LOADED_VALUE
        cache[KEY] shouldBe NEWER_VALUE
    }

    test("a removal during a load never brings the key back") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        val gate = Gate<String>()
        val leader = async { cache.getOrLoad(KEY) { gate.pass() } }
        testCoroutineScheduler.runCurrent()
        cache.remove(KEY) shouldBe null
        gate.open(LOADED_VALUE)
        leader.await() shouldBe LOADED_VALUE
        (KEY in cache) shouldBe false
    }

    test("a clear during a load never brings the key back") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        val gate = Gate<String>()
        val leader = async { cache.getOrLoad(KEY) { gate.pass() } }
        testCoroutineScheduler.runCurrent()
        cache.clear()
        gate.open(LOADED_VALUE)
        leader.await() shouldBe LOADED_VALUE
        cache.size shouldBe 0
    }

    test("an invalidated load never disturbs the load that follows it") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        val stale = Gate<String>()
        val fresh = Gate<String>()
        val first = async { cache.getOrLoad(KEY) { stale.pass() } }
        testCoroutineScheduler.runCurrent()
        cache.remove(KEY)
        val second = async { cache.getOrLoad(KEY) { fresh.pass() } }
        val follower = async { cache.getOrLoad(KEY) { fresh.pass() } }
        testCoroutineScheduler.runCurrent()
        stale.open(VALUE)
        first.await() shouldBe VALUE
        cache.peek(KEY) shouldBe null
        fresh.open(LOADED_VALUE)
        second.await() shouldBe LOADED_VALUE
        follower.await() shouldBe LOADED_VALUE
        fresh.entered shouldBe 1
        cache[KEY] shouldBe LOADED_VALUE
    }

    test("a failed reload serves the stale value within its grace") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) {
            expireAfterWrite = LIFETIME
            staleOnFailure = GRACE
        }
        cache.put(key = KEY, value = VALUE)
        delay(LIFETIME)
        val gate = Gate<String>()
        val leader = async { cache.getOrLoad(KEY) { gate.pass() } }
        val waiter = async { cache.getOrLoad(KEY) { gate.pass() } }
        testCoroutineScheduler.runCurrent()
        gate.fail(IllegalStateException("reload failed"))
        leader.await() shouldBe VALUE
        waiter.await() shouldBe VALUE
        cache.snapshot().staleServed shouldBe 2L
        cache.snapshot().loadFailures shouldBe 1L
        delay(GRACE)
        failureOf { cache.getOrLoad(KEY) { error("still failing") } }.shouldBeInstanceOf<IllegalStateException>()
    }

    test("a failed load serves no value that died while it ran") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) {
            expireAfterWrite = LIFETIME
            staleOnFailure = GRACE
        }
        cache.put(key = KEY, value = VALUE)
        delay(LIFETIME)
        val gate = Gate<String>()
        val leader = async { failureOf { cache.getOrLoad(KEY) { gate.pass() } } }
        testCoroutineScheduler.runCurrent()
        delay(GRACE)
        gate.fail(IllegalStateException(LOAD_FAILED))
        leader.await().shouldBeInstanceOf<IllegalStateException>()
        cache.snapshot().staleServed shouldBe 0L
    }

    test("a failed load serves a value written while it ran and a failure without grace passes") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) { staleOnFailure = GRACE }
        val gate = Gate<String>()
        val leader = async { cache.getOrLoad(KEY) { gate.pass() } }
        testCoroutineScheduler.runCurrent()
        cache.put(key = KEY, value = NEWER_VALUE)
        gate.fail(IllegalStateException(LOAD_FAILED))
        leader.await() shouldBe NEWER_VALUE
        cache.snapshot().staleServed shouldBe 0L
        val strict = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        strict.put(key = KEY, value = VALUE)
        failureOf { strict.getOrLoad("missing") { error(LOAD_FAILED) } }.shouldBeInstanceOf<IllegalStateException>()
    }

    test("a loaded value the weigher refuses is a failed load") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) {
            maximumWeight = 1L
            weigher { _, _ -> error("unweighable") }
        }
        val failure = failureOf { cache.getOrLoad(KEY) { LOADED_VALUE } }
        failure.shouldBeInstanceOf<IllegalArgumentException>()
        cache.snapshot().loadFailures shouldBe 1L
        cache.size shouldBe 0
    }

    test("a cancellation that does not come from the caller is a failed load") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        val failure = failureOf { cache.getOrLoad(KEY) { throw CancellationException("callee timed out") } }
        val foreign = failure.shouldBeInstanceOf<IllegalStateException>()
        foreign.cause.shouldBeInstanceOf<CancellationException>()
        cache.snapshot().loadFailures shouldBe 1L
    }

    test("a lookup that meets an expired entry removes it and loads anew") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource)) { expireAfterWrite = LIFETIME }
        cache.put(key = KEY, value = VALUE)
        delay(LIFETIME)
        cache.getOrLoad(KEY) { LOADED_VALUE } shouldBe LOADED_VALUE
        cache.snapshot().expirations shouldBe 1L
    }

    test("a decorated loader caches its values") {
        val cache = smallCache(testEnvironment(testCoroutineScheduler.timeSource))
        var calls = 0
        val upper = cache.decorate { key ->
            calls += 1
            key.uppercase()
        }
        upper(KEY) shouldBe "ALICE"
        upper(KEY) shouldBe "ALICE"
        calls shouldBe 1
    }
})
