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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val THREADS: Int = 8
private const val KEYS: Int = 400
private const val ROUNDS: Int = 3
private const val SPIN: Int = 200
private const val SMALL: Int = 16
private const val CALLS_PER_THREAD: Int = 5_000
private const val WRITE_EVERY: Int = 7
private const val TIMEOUT_SECONDS: Long = 60L

class CacheRaceTest : FunSpec({
    test("concurrent blocking and suspending callers load every key exactly once") {
        val cache = cache<Int, Int>("race.loads") { maximumSize = KEYS }
        val loads = ConcurrentHashMap<Int, AtomicInteger>()
        val loader = { key: Int ->
            loads.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()
            repeat(SPIN) { Thread.onSpinWait() }
            key * 2
        }
        onThreads { thread ->
            repeat(ROUNDS * KEYS) { step ->
                val key = (step + thread * KEYS / THREADS) % KEYS
                cache.loadOn(thread, key, loader) shouldBe key * 2
            }
        }
        loads.size shouldBe KEYS
        loads.values.forEach { count -> count.get() shouldBe 1 }
        val snapshot = cache.snapshot()
        snapshot.loads shouldBe KEYS.toLong()
        snapshot.hits + snapshot.misses shouldBe (THREADS * ROUNDS * KEYS).toLong()
    }

    test("every lookup is counted once while writes, removals and evictions race") {
        val cache = cache<Int, Int>("race.accounting") { maximumSize = SMALL }
        onThreads { thread ->
            repeat(CALLS_PER_THREAD) { step ->
                val key = step * (thread + 1) % (SMALL * 2)
                if (step % WRITE_EVERY == 0) cache.put(key = key, value = step)
                if (step % WRITE_EVERY == 1) cache.remove(key)
                cache.getOrLoadBlocking(key) { loaded -> loaded }
            }
        }
        val snapshot = cache.snapshot()
        snapshot.hits + snapshot.misses shouldBe (THREADS * CALLS_PER_THREAD).toLong()
        (cache.size <= SMALL) shouldBe true
        cache.cleanUp() shouldBe 0
    }
})

private fun Cache<Int, Int>.loadOn(thread: Int, key: Int, loader: (Int) -> Int): Int {
    if (thread % 2 == 0) return getOrLoadBlocking(key, loader)
    return runBlocking { getOrLoad(key) { loaded -> loader(loaded) } }
}

private fun onThreads(work: (Int) -> Unit) {
    val pool = Executors.newFixedThreadPool(THREADS)
    val start = CountDownLatch(1)
    val failures = ConcurrentHashMap.newKeySet<Throwable>()
    repeat(THREADS) { thread ->
        pool.execute {
            start.await()
            val failure = runCatching { work(thread) }.exceptionOrNull()
            if (failure != null) failures += failure
        }
    }
    start.countDown()
    pool.shutdown()
    pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
    failures shouldBe emptySet()
}
