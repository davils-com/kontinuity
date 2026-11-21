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

package com.davils.kontinuity.bulkhead

import com.davils.kontinuity.bulkhead.internal.DispatcherReporter
import com.davils.kontinuity.bulkhead.internal.MeterScheme
import com.davils.kontinuity.bulkhead.internal.Occupancy
import com.davils.kontinuity.bulkhead.internal.PoolMeters
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.Stage
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.failure.contained
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.time.ComparableTimeMark
import kotlin.time.TimeSource

/**
 * A dispatcher bulkhead: it runs its calls on a dispatcher of the caller with at most
 * [DispatcherBulkheadConfig.maxConcurrentCalls] of them in parallel and admits at most
 * [DispatcherBulkheadConfig.queueCapacity] more that wait for their turn, the counterpart of the thread pool bulkhead
 * of resilience4j for Kotlin Multiplatform.
 *
 * Create one with `dispatcherBulkhead("reports", Dispatchers.IO) { maxConcurrentCalls = 4 }` or take it from a
 * [DispatcherBulkheadRegistry]. kontinuity never chooses a dispatcher and starts no thread: the caller hands one in,
 * and the bulkhead runs every call with `withContext` on a view of it limited to
 * [DispatcherBulkheadConfig.maxConcurrentCalls] in parallel. Unlike other stages, a call therefore runs on that
 * dispatcher, not on the one of its caller, which isolates blocking or slow work from the rest of the application.
 * A call that finds running and queued calls at the capacity of `maxConcurrentCalls + queueCapacity` is refused at
 * once with a [BulkheadFullException]; nothing ever waits for room in the queue.
 *
 * At most `maxConcurrentCalls` calls execute at the same instant. A call that suspends frees its thread of the
 * dispatcher for another call, as on every dispatcher, but keeps its place in the capacity until it ends. The
 * cancellation of a caller whose call has not started yet removes it from the queue and is counted as cancelled; the
 * cancellation of a running call ends it as finished; both are rethrown.
 *
 * A dispatcher bulkhead is safe to use from many coroutines and threads at once; reading [snapshot] changes nothing.
 *
 * @property name the name of the bulkhead.
 * @property config the configuration.
 * @param dispatcher the dispatcher of the caller that runs the calls.
 * @param environment the environment of the bulkhead.
 * @since 1.0.0
 */
public class DispatcherBulkhead internal constructor(
    override val name: InstanceName,
    public val config: DispatcherBulkheadConfig,
    dispatcher: CoroutineDispatcher,
    environment: Environment,
) : Stage {
    private val lane: CoroutineDispatcher = laneOf(dispatcher)
    private val occupancy: Occupancy = Occupancy(config)
    private val sink: EventSink<DispatcherBulkheadEvent> = EventSink(environment, DispatcherBulkheadEvent.TOPIC)
    private val timeSource: TimeSource.WithComparableMarks = environment.timeSource
    private val reporter: DispatcherReporter = DispatcherReporter(
        name = name,
        sink = sink,
        meters = environment.meters?.let { registry -> PoolMeters(registry, name, MeterScheme.Dispatcher) },
        source = occupancy,
    )

    init {
        reporter.changed()
    }

    override val kind: ResilienceKind get() = ResilienceKind.DispatcherBulkhead

    /**
     * The events of the bulkhead, hot and without replay; a slow collector loses the oldest events.
     *
     * @since 1.0.0
     */
    public val events: Flow<DispatcherBulkheadEvent> get() = sink.events

    /**
     * Returns the running and queued calls and the totals of the bulkhead at this moment.
     *
     * @return the snapshot.
     * @since 1.0.0
     */
    public fun snapshot(): DispatcherBulkheadSnapshot = occupancy.snapshot()

    /**
     * Runs [block] on the dispatcher of the bulkhead once it has a turn there and returns its result to the caller.
     *
     * @param T the type of the result.
     * @param block the protected call; it runs on the dispatcher of the bulkhead.
     * @return the result of [block].
     * @throws BulkheadFullException if running and queued calls fill the capacity.
     * @throws Throwable what [block] throws.
     * @since 1.0.0
     */
    override suspend fun <T> execute(block: suspend () -> T): T {
        if (!occupancy.admit()) {
            reporter.rejected()
            throw BulkheadFullException(instance = name, kind = kind, isQueueFull = true)
        }
        reporter.changed()
        val admitted = timeSource.markNow()
        var isStarted = false
        try {
            return withContext(lane) {
                isStarted = true
                run(admitted, block)
            }
        } finally {
            if (!isStarted) cancelled()
        }
    }

    /**
     * Shows the name and the limits.
     *
     * @return the bulkhead as text.
     * @since 1.0.0
     */
    override fun toString(): String = "DispatcherBulkhead($name, maxConcurrentCalls=${config.maxConcurrentCalls}, " +
        "queueCapacity=${config.queueCapacity})"

    private suspend fun <T> run(admitted: ComparableTimeMark, block: suspend () -> T): T {
        occupancy.start()
        reporter.permitted(admitted.elapsedNow())
        val start = timeSource.markNow()
        try {
            return block()
        } finally {
            occupancy.finish()
            reporter.finished(start.elapsedNow())
        }
    }

    private fun cancelled() {
        occupancy.cancel()
        reporter.cancelled()
    }

    private fun laneOf(dispatcher: CoroutineDispatcher): CoroutineDispatcher {
        val limited = contained { dispatcher.limitedParallelism(config.maxConcurrentCalls) }
        return requireNotNull(limited.getOrNull()) {
            "The dispatcher bulkhead $name needs a dispatcher that can limit its parallelism"
        }
    }
}
