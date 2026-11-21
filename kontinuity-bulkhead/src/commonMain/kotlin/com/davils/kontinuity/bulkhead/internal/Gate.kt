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
import com.davils.kontinuity.bulkhead.BulkheadEvent
import com.davils.kontinuity.bulkhead.BulkheadFullException
import com.davils.kontinuity.bulkhead.BulkheadPermit
import com.davils.kontinuity.bulkhead.BulkheadSnapshot
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kore.core.annotation.DavilsInternal
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

internal class Gate(val name: InstanceName, config: BulkheadConfig, environment: Environment) {
    private val sink: EventSink<BulkheadEvent> = EventSink(environment, BulkheadEvent.TOPIC)
    val seats: Seats = Seats(config)
    val timeSource: TimeSource.WithComparableMarks = environment.timeSource
    val reporter: BulkheadReporter = BulkheadReporter(
        name = name,
        sink = sink,
        meters = environment.meters?.let { registry -> PoolMeters(registry, name, MeterScheme.Bulkhead) },
        source = seats,
    )

    init {
        reporter.changed()
    }

    val events: Flow<BulkheadEvent> get() = sink.events

    val config: BulkheadConfig get() = seats.config

    fun snapshot(): BulkheadSnapshot = seats.snapshot()

    fun tryAcquire(): BulkheadPermit? {
        val entry = seats.enter(mayWait = false, wake = NO_WAKE)
        if (entry != Entry.Granted) {
            reporter.rejected(isQueueFull = false)
            return null
        }
        return granted(Duration.ZERO)
    }

    suspend fun acquire(timeout: Duration): BulkheadPermit {
        checkTimeout(timeout)
        val start = timeSource.markNow()
        val entry = seats.enter(mayWait = timeout.isPositive(), wake = NO_WAKE)
        return admitted(entry, start) { waiter -> awaitTurn(waiter, timeout) }
    }

    inline fun admitted(entry: Entry, start: ComparableTimeMark, wait: (Waiter) -> Boolean): BulkheadPermit {
        val isGranted = when (entry) {
            Entry.Granted -> true
            is Entry.Refused -> throw refused(entry.isQueueFull)
            is Entry.Queued -> waited(entry.waiter, wait)
        }
        if (!isGranted) throw refused(isQueueFull = false)
        return granted(start.elapsedNow())
    }

    inline fun waited(waiter: Waiter, wait: (Waiter) -> Boolean): Boolean {
        reporter.changed()
        return wait(waiter)
    }

    fun checkTimeout(timeout: Duration) {
        val isBounded = timeout >= Duration.ZERO && timeout <= BulkheadConfig.HARD_WAIT
        require(isBounded) { "The wait for a permit of the bulkhead $name must be from zero to one hour" }
    }

    fun refused(isQueueFull: Boolean): BulkheadFullException {
        reporter.rejected(isQueueFull)
        return BulkheadFullException(instance = name, kind = ResilienceKind.Bulkhead, isQueueFull = isQueueFull)
    }

    fun granted(waited: Duration): BulkheadPermit {
        reporter.permitted(waited)
        return BulkheadPermit(this, timeSource.markNow())
    }

    fun cancel(waiter: Waiter) {
        wake(seats.cancel(waiter))
        reporter.cancelled()
    }

    fun release(held: ComparableTimeMark) {
        wake(seats.release())
        reporter.finished(held.elapsedNow())
    }

    fun resize(config: BulkheadConfig) {
        wake(seats.resize(config))
        reporter.changed()
    }

    private suspend fun awaitTurn(waiter: Waiter, timeout: Duration): Boolean {
        var isDecided = false
        try {
            withTimeoutOrNull(timeout) {
                suspendCancellableCoroutine<Unit> { continuation -> park(waiter, continuation) }
            }
            isDecided = true
        } finally {
            if (!isDecided) cancel(waiter)
        }
        return seats.finish(waiter)
    }

    private fun park(waiter: Waiter, continuation: CancellableContinuation<Unit>) {
        continuation.invokeOnCancellation { wake(seats.abandon(waiter)) }
        val resume = seats.attach(waiter) { continuation.resume(Unit) }
        resume()
    }

    private fun wake(wakes: List<() -> Unit>) {
        wakes.forEach { wake -> wake() }
    }

    private companion object {
        val NO_WAKE: () -> Unit = {}
    }
}
