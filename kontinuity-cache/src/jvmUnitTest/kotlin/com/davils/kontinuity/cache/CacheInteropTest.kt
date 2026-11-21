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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

private const val KEY: String = "alice"
private const val VALUE: String = "Alice"
private const val NEVER: String = "never loaded"
private const val TIMEOUT_SECONDS: Long = 30L
private const val JOIN_MILLIS: Long = 30_000L

class CacheInteropTest : FunSpec({
    test("a blocking lookup loads a miss once and returns a hit") {
        val cache = cache<String, String>("blocking")
        val calls = AtomicInteger(0)
        cache.getOrLoadBlocking(KEY) { key ->
            calls.incrementAndGet()
            key.uppercase()
        } shouldBe "ALICE"
        cache.getOrLoadBlocking(KEY) { error(NEVER) } shouldBe "ALICE"
        calls.get() shouldBe 1
    }

    test("a blocking waiter receives the shared value and an exception of its own for a shared failure") {
        val cache = cache<String, String>("shared")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = IllegalStateException("load failed")
        val leaderFailure = AtomicReference<Throwable>()
        val waiterFailure = AtomicReference<Throwable>()
        val leader = thread {
            val thrown = failureOf {
                cache.getOrLoadBlocking(KEY) {
                    entered.countDown()
                    release.await()
                    throw failure
                }
            }
            leaderFailure.set(thrown)
        }
        entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
        val waiter = thread { waiterFailure.set(failureOf { cache.getOrLoadBlocking(KEY) { error(NEVER) } }) }
        awaitWaiting(waiter)
        release.countDown()
        leader.join(JOIN_MILLIS)
        waiter.join(JOIN_MILLIS)
        leaderFailure.get() shouldBeSameInstanceAs failure
        val shared = waiterFailure.get().shouldBeInstanceOf<CacheLoadException>()
        shared.cause shouldBeSameInstanceAs failure
    }

    test("an interrupted loading thread lets a blocking waiter load again") {
        val cache = cache<String, String>("interrupted")
        val entered = CountDownLatch(1)
        val calls = AtomicInteger(0)
        val leaderFailure = AtomicReference<Throwable>()
        val value = AtomicReference<String>()
        val loader = { _: String ->
            val call = calls.incrementAndGet()
            if (call == 1) entered.countDown()
            if (call == 1) CountDownLatch(1).await()
            VALUE
        }
        val leader = thread { leaderFailure.set(failureOf { cache.getOrLoadBlocking(KEY, loader) }) }
        entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
        val waiter = thread { value.set(cache.getOrLoadBlocking(KEY, loader)) }
        awaitWaiting(waiter)
        leader.interrupt()
        leader.join(JOIN_MILLIS)
        waiter.join(JOIN_MILLIS)
        leaderFailure.get().shouldBeInstanceOf<InterruptedException>()
        value.get() shouldBe VALUE
        calls.get() shouldBe 2
        cache.snapshot().loads shouldBe 1L
    }

    test("an interrupted blocking waiter stops waiting and keeps its interrupt flag") {
        val cache = cache<String, String>("waiting")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val keptInterrupt = AtomicReference<Boolean>()
        val leader = thread {
            cache.getOrLoadBlocking(KEY) {
                entered.countDown()
                release.await()
                VALUE
            }
        }
        entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
        val waiter = thread {
            val failure = failureOf { cache.getOrLoadBlocking(KEY) { error(NEVER) } }
            keptInterrupt.set(failure is InterruptedException && Thread.currentThread().isInterrupted)
        }
        awaitWaiting(waiter)
        waiter.interrupt()
        waiter.join(JOIN_MILLIS)
        release.countDown()
        leader.join(JOIN_MILLIS)
        keptInterrupt.get() shouldBe true
        cache[KEY] shouldBe VALUE
    }

    test("a blocking load that the weigher refuses fails") {
        val cache = cache<String, String>("refused") {
            maximumWeight = 1L
            weigher { _, _ -> -1 }
        }
        shouldThrow<IllegalArgumentException> { cache.getOrLoadBlocking(KEY) { VALUE } }
    }
})

private fun awaitWaiting(thread: Thread) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
    while (thread.state != Thread.State.WAITING && System.nanoTime() < deadline) Thread.onSpinWait()
    thread.state shouldBe Thread.State.WAITING
}

private fun failureOf(block: () -> Unit): Throwable? = runCatching(block).exceptionOrNull()
