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

package com.davils.kontinuity.bulkhead

import com.davils.kontinuity.bulkhead.internal.Gate
import com.davils.kontinuity.bulkhead.internal.Waiter
import com.davils.kore.core.failure.rethrowIfFatal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.locks.LockSupport
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Runs the blocking [block] while it holds a permit and gives the permit back however [block] ends, for Java and
 * blocking code.
 *
 * When no permit is free, the calling thread joins the same fair queue as suspended callers and parks for at most
 * [BulkheadConfig.maxWaitDuration]; with the default wait of zero it never parks. The wait is measured in real time,
 * whatever the time source of the environment. Never call it from a coroutine: it blocks its thread.
 *
 * @receiver the bulkhead.
 * @param T the type of the result.
 * @param block the protected call.
 * @return the result of [block].
 * @throws BulkheadFullException if the bulkhead refuses the call.
 * @throws InterruptedException if the thread is interrupted while it waits; it leaves the queue and is counted as
 *   cancelled.
 * @throws Throwable what [block] throws.
 * @since 1.0.0
 */
@Throws(InterruptedException::class)
public fun <T> Bulkhead.executeBlocking(block: () -> T): T {
    val permit = acquireBlocking()
    try {
        return block()
    } finally {
        permit.release()
    }
}

/**
 * Starts the asynchronous [block] while it holds a permit and gives the permit back when its stage completes.
 *
 * The permit is taken as [executeBlocking] takes it, so the calling thread may park for at most
 * [BulkheadConfig.maxWaitDuration]. A refusal completes the returned stage exceptionally with a
 * [BulkheadFullException] instead of throwing; a [block] that throws gives the permit back at once.
 *
 * @receiver the bulkhead.
 * @param T the type of the result.
 * @param block starts the protected call.
 * @return the stage of the call.
 * @throws InterruptedException if the thread is interrupted while it waits for a permit.
 * @since 1.0.0
 */
@Throws(InterruptedException::class)
public fun <T> Bulkhead.executeCompletionStage(block: () -> CompletionStage<T>): CompletionStage<T> {
    val permit = try {
        acquireBlocking()
    } catch (refusal: BulkheadFullException) {
        return CompletableFuture.failedFuture(refusal)
    }
    val started = runCatching(block)
    val failure = started.exceptionOrNull()
    if (failure != null) permit.release()
    failure?.rethrowIfFatal()
    val stage = started.getOrElse { problem -> CompletableFuture.failedFuture(problem) }
    return stage.whenComplete { _, _ -> permit.release() }
}

private fun Bulkhead.acquireBlocking(): BulkheadPermit {
    val timeout = config.maxWaitDuration
    val thread = Thread.currentThread()
    val start = gate.timeSource.markNow()
    val entry = gate.seats.enter(mayWait = timeout.isPositive()) { LockSupport.unpark(thread) }
    return gate.admitted(entry, start) { waiter -> parked(gate, waiter, timeout) }
}

private fun parked(gate: Gate, waiter: Waiter, timeout: Duration): Boolean {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    var remaining = timeout
    while (remaining.isPositive() && !gate.seats.isGranted(waiter)) {
        LockSupport.parkNanos(gate, remaining.inWholeNanoseconds)
        if (Thread.interrupted()) interrupted(gate, waiter)
        remaining = -deadline.elapsedNow()
    }
    return gate.seats.finish(waiter)
}

private fun interrupted(gate: Gate, waiter: Waiter): Nothing {
    gate.cancel(waiter)
    throw InterruptedException("The wait for a permit of the bulkhead ${gate.name} was interrupted")
}
