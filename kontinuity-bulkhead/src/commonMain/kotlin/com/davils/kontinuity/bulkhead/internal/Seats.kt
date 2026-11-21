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

@file:OptIn(DavilsInternal::class)

package com.davils.kontinuity.bulkhead.internal

import com.davils.kontinuity.bulkhead.BulkheadConfig
import com.davils.kontinuity.bulkhead.BulkheadSnapshot
import com.davils.kontinuity.core.telemetry.Guard
import com.davils.kontinuity.core.telemetry.guard
import com.davils.kore.core.annotation.DavilsInternal

internal class Seats(config: BulkheadConfig) : LevelSource {
    private val guard: Guard = guard()
    private val queue: WaitQueue = WaitQueue()
    private val counts: Counts = Counts()
    private var current: BulkheadConfig = config
    private var running: Int = 0
    private var changes: Long = 0L

    val config: BulkheadConfig get() = guard.locked { current }

    override val version: Long get() = guard.locked { changes }

    fun enter(mayWait: Boolean, wake: () -> Unit): Entry = guard.locked { entry(mayWait, wake) }

    fun attach(waiter: Waiter, wake: () -> Unit): () -> Unit = guard.locked {
        waiter.wake = wake
        wake.takeIf { waiter.state == WaiterState.Granted } ?: NOTHING
    }

    fun isGranted(waiter: Waiter): Boolean = guard.locked { waiter.state == WaiterState.Granted }

    fun finish(waiter: Waiter): Boolean = guard.locked {
        val isGranted = waiter.state == WaiterState.Granted
        if (isGranted) take(waiter)
        if (!isGranted) refuse(waiter)
        isGranted
    }

    fun abandon(waiter: Waiter): List<() -> Unit> = guard.locked { abandoned(waiter) }

    fun cancel(waiter: Waiter): List<() -> Unit> = guard.locked {
        counts.cancelled += 1L
        abandoned(waiter)
    }

    fun release(): List<() -> Unit> = guard.locked {
        running -= 1
        counts.finished += 1L
        handOver()
    }

    fun resize(config: BulkheadConfig): List<() -> Unit> = guard.locked {
        current = config
        handOver()
    }

    override fun levels(): Levels = guard.locked {
        Levels(version = changes, first = available(), second = queue.size)
    }

    fun snapshot(): BulkheadSnapshot = guard.locked {
        BulkheadSnapshot(
            availableConcurrentCalls = available(),
            maxAllowedConcurrentCalls = current.maxConcurrentCalls,
            concurrentCalls = running,
            waitingCalls = queue.size,
            permitted = counts.permitted,
            rejected = counts.rejected,
            finished = counts.finished,
            cancelled = counts.cancelled,
        )
    }

    private fun entry(mayWait: Boolean, wake: () -> Unit): Entry {
        if (running < current.maxConcurrentCalls) {
            running += 1
            counts.permitted += 1L
            changes += 1L
            return Entry.Granted
        }
        return queued(mayWait, wake)
    }

    private fun queued(mayWait: Boolean, wake: () -> Unit): Entry {
        val isQueueFull = queue.size >= current.maxWaitingCalls
        if (!mayWait || isQueueFull) {
            counts.rejected += 1L
            return Entry.Refused(isQueueFull = mayWait && isQueueFull)
        }
        val waiter = Waiter(wake)
        queue.add(waiter)
        changes += 1L
        return Entry.Queued(waiter)
    }

    private fun take(waiter: Waiter) {
        waiter.moveTo(WaiterState.Taken)
        counts.permitted += 1L
    }

    private fun refuse(waiter: Waiter) {
        drop(waiter)
        counts.rejected += 1L
    }

    private fun abandoned(waiter: Waiter): List<() -> Unit> {
        val hasPermit = waiter.state == WaiterState.Granted
        drop(waiter)
        if (!hasPermit) return emptyList()
        running -= 1
        return handOver()
    }

    private fun drop(waiter: Waiter) {
        if (waiter.state == WaiterState.Waiting) queue.remove(waiter)
        waiter.moveTo(WaiterState.Abandoned)
        changes += 1L
    }

    private fun handOver(): List<() -> Unit> {
        changes += 1L
        val woken = ArrayList<() -> Unit>()
        var next = nextWaiter()
        while (next != null) {
            next.moveTo(WaiterState.Granted)
            running += 1
            woken.add(next.wake)
            next = nextWaiter()
        }
        return woken
    }

    private fun nextWaiter(): Waiter? {
        if (running >= current.maxConcurrentCalls) return null
        return queue.poll()
    }

    private fun available(): Int = (current.maxConcurrentCalls - running).coerceAtLeast(0)

    private companion object {
        val NOTHING: () -> Unit = {}
    }
}
