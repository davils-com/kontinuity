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

package com.davils.kontinuity.ratelimiter

import com.davils.kontinuity.ratelimiter.internal.Reservation
import com.davils.kore.core.failure.rethrowIfFatal
import kotlin.time.Duration

private const val NANOS_PER_MILLI: Long = 1_000_000L

/**
 * Takes [permits] permits for blocking code, blocking the current thread until they are due but at most [timeout].
 *
 * The thread sleeps; the limiter starts no thread of its own. An interrupt while the thread waits gives the
 * reservation back, sets the interrupt flag of the thread again and ends the wait with an [IllegalStateException]
 * whose cause is the `InterruptedException`; the request counts as cancelled.
 *
 * @receiver the limiter.
 * @param permits how many permits to take, from 1 to [RateLimiter.limitForPeriod]; one by default.
 * @param timeout how long to wait at most, from zero to [RateLimiterConfig.HARD_TIMEOUT]; the timeout of the limiter
 *   by default.
 * @throws IllegalArgumentException if [permits] or [timeout] is out of its bounds.
 * @throws RequestNotPermittedException if the permits are not free within [timeout] or the queue is full.
 * @throws IllegalStateException if the thread is interrupted while it waits.
 * @since 1.0.0
 */
public fun RateLimiter.acquireBlocking(permits: Int = 1, timeout: Duration = this.timeout) {
    val reservation = gate.reserve(permits, timeout)
    if (reservation.isWaiting) sleepFor(reservation)
    gate.granted(reservation)
}

private fun RateLimiter.sleepFor(reservation: Reservation) {
    val nanos = reservation.waitNanos
    try {
        Thread.sleep(nanos / NANOS_PER_MILLI, (nanos % NANOS_PER_MILLI).toInt())
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        gate.leave()
        gate.cancelled(reservation)
        throw IllegalStateException("The wait for the permits of the rate limiter $name was interrupted", interrupted)
    }
    gate.leave()
}

/**
 * Takes [permits] permits like [acquireBlocking] and runs the blocking [block], for Java and blocking code; a failure
 * that [RateLimiterConfig.drainOnFailures] records or a result that [RateLimiterConfig.drainOnResults] accepts drains
 * the remaining permits of the current period. Fatal errors pass through.
 *
 * @receiver the limiter.
 * @param T the type of the result.
 * @param permits how many permits the call needs, from 1 to [RateLimiter.limitForPeriod]; one by default.
 * @param block the protected call.
 * @return the result of [block].
 * @throws IllegalArgumentException if [permits] is out of its bounds.
 * @throws RequestNotPermittedException if the permits are not free within the timeout or the queue is full.
 * @throws IllegalStateException if the thread is interrupted while it waits.
 * @throws Throwable what [block] throws, or what a rule of [RateLimiterConfig.drainOnResults] throws.
 * @since 1.0.0
 */
public fun <T> RateLimiter.executeBlocking(permits: Int = 1, block: () -> T): T {
    acquireBlocking(permits)
    val result = runCatching(block)
    val value = result.getOrElse { failure ->
        failure.rethrowIfFatal()
        throw gate.failed(failure)
    }
    gate.succeeded(value)
    return value
}
