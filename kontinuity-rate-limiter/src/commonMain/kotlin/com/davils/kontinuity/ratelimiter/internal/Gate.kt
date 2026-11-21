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

@file:OptIn(ExperimentalAtomicApi::class)

package com.davils.kontinuity.ratelimiter.internal

import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.filter.FailureVerdict
import com.davils.kontinuity.ratelimiter.RateLimiterAlgorithm
import com.davils.kontinuity.ratelimiter.RateLimiterConfig
import com.davils.kontinuity.ratelimiter.RateLimiterSnapshot
import com.davils.kontinuity.ratelimiter.RequestNotPermittedException
import com.davils.kore.core.failure.contained
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

internal class Gate(
    private val name: InstanceName,
    private val config: RateLimiterConfig,
    private val reporter: Reporter,
    timeSource: TimeSource.WithComparableMarks,
) {
    val window: PermitWindow = windowOf(config)
    private val started: ComparableTimeMark = timeSource.markNow()
    private val current = AtomicReference(Limits(config.limitForPeriod, config.timeout))
    private val waiting = AtomicInt(0)
    private val totals = Totals()

    val limits: Limits get() = current.load()

    val waitingCallers: Int get() = waiting.load()

    val availablePermissions: Int get() = window.available(nowNanos(), limits.limitForPeriod)

    fun snapshot(): RateLimiterSnapshot {
        val now = limits
        return RateLimiterSnapshot(
            availablePermissions = availablePermissions,
            waitingCallers = waitingCallers,
            limitForPeriod = now.limitForPeriod,
            timeout = now.timeout,
            acquired = totals.acquired,
            rejected = totals.rejected,
            cancelled = totals.cancelled,
            permitsGranted = totals.permitsGranted,
        )
    }

    suspend fun acquire(permits: Int, timeout: Duration) {
        val reservation = reserve(permits, timeout)
        if (reservation.isWaiting) await(reservation)
        granted(reservation)
    }

    fun reserve(permits: Int, timeout: Duration): Reservation {
        val limits = checked(permits)
        require(isValidTimeout(timeout)) { "The timeout of the rate limiter $name must be from zero to one hour" }
        val reservation = queued(permits = permits, timeoutNanos = timeout.inWholeNanoseconds, limits = limits)
        if (!reservation.isGranted) throw refused(reservation)
        return reservation
    }

    fun tryAcquire(permits: Int): Boolean {
        val limits = checked(permits)
        val reservation = window.reserve(
            permits = permits,
            nowNanos = nowNanos(),
            maxWaitNanos = 0L,
            limit = limits.limitForPeriod,
        )
        if (reservation.isGranted) granted(reservation)
        if (!reservation.isGranted) reject(reservation)
        return reservation.isGranted
    }

    fun reservePermission(permits: Int): Duration? {
        val limits = checked(permits)
        val reservation = unheld(permits, limits)
        if (!reservation.isGranted) {
            reject(reservation)
            return null
        }
        granted(reservation)
        return reservation.wait
    }

    fun granted(reservation: Reservation) {
        totals.acquired(reservation.permits)
        reporter.acquired(reservation.permits, reservation.wait)
        levels()
    }

    fun cancelled(reservation: Reservation) {
        val isRefunded = reservation.refund(nowNanos())
        totals.cancelled()
        reporter.cancelled(reservation.permits, isRefunded)
        levels()
    }

    fun leave() {
        waiting.addAndFetch(-1)
    }

    fun drain(): Int {
        val drained = window.drain(nowNanos(), limits.limitForPeriod)
        reporter.drained(drained)
        levels()
        return drained
    }

    fun failed(failure: Throwable): Throwable {
        val filter = config.drainOnFailures ?: return failure
        if (filter.verdictOf(failure) == FailureVerdict.Recorded) drain()
        return failure
    }

    fun succeeded(value: Any?) {
        val filter = config.drainOnResults
        if (filter.isEmpty) return
        val judged = contained { filter.isFailure(value) }
        val problem = judged.exceptionOrNull()
        if (problem != null) throw failed(problem)
        if (judged.getOrThrow()) drain()
    }

    fun changeLimitForPeriod(limit: Int) {
        require(limit in 1..RateLimiterConfig.HARD_LIMIT_FOR_PERIOD) {
            "The limit for period of the rate limiter $name must be from 1 to 1000000"
        }
        val bucket = config.algorithm as? RateLimiterAlgorithm.TokenBucket
        require(bucket == null || bucket.fits(limit, config.limitRefreshPeriod)) {
            "The limit for period of the rate limiter $name must suit its token bucket"
        }
        change { old -> Limits(limitForPeriod = limit, timeout = old.timeout) }
    }

    fun changeTimeout(timeout: Duration) {
        require(isValidTimeout(timeout)) { "The timeout of the rate limiter $name must be from zero to one hour" }
        change { old -> Limits(limitForPeriod = old.limitForPeriod, timeout = timeout) }
    }

    private suspend fun await(reservation: Reservation) {
        var isServed = false
        try {
            delay(reservation.wait)
            isServed = true
        } finally {
            withContext(NonCancellable) { settle(reservation, isServed) }
        }
    }

    private fun settle(reservation: Reservation, isServed: Boolean) {
        leave()
        if (!isServed) cancelled(reservation)
    }

    private fun queued(permits: Int, timeoutNanos: Long, limits: Limits): Reservation {
        val limit = limits.limitForPeriod
        val immediate = window.reserve(permits = permits, nowNanos = nowNanos(), maxWaitNanos = 0L, limit = limit)
        val isWaitAllowed = !immediate.isGranted && immediate.waitNanos <= timeoutNanos
        if (!isWaitAllowed || !enter()) return immediate
        val queued = window.reserve(
            permits = permits,
            nowNanos = nowNanos(),
            maxWaitNanos = timeoutNanos,
            limit = limit,
        )
        if (!queued.isWaiting) leave()
        if (queued.isWaiting) levels()
        return queued
    }

    private fun unheld(permits: Int, limits: Limits): Reservation {
        val limit = limits.limitForPeriod
        val timeoutNanos = limits.timeout.inWholeNanoseconds
        val immediate = window.reserve(permits = permits, nowNanos = nowNanos(), maxWaitNanos = 0L, limit = limit)
        val hasRoom = waiting.load() < config.maxWaitingCallers
        val isWaitAllowed = !immediate.isGranted && immediate.waitNanos <= timeoutNanos && hasRoom
        if (!isWaitAllowed) return immediate
        return window.reserve(permits = permits, nowNanos = nowNanos(), maxWaitNanos = timeoutNanos, limit = limit)
    }

    private fun enter(): Boolean {
        while (true) {
            val count = waiting.load()
            if (count >= config.maxWaitingCallers) return false
            if (waiting.compareAndSet(expectedValue = count, newValue = count + 1)) return true
        }
    }

    private fun refused(reservation: Reservation): RequestNotPermittedException {
        reject(reservation)
        return RequestNotPermittedException(name, reservation.permits, reservation.wait)
    }

    private fun reject(reservation: Reservation) {
        totals.rejected()
        reporter.rejected(reservation.permits, reservation.wait)
        levels()
    }

    private fun change(next: (Limits) -> Limits) {
        val changed = next(current.swap(next))
        window.retune(nowNanos(), changed.limitForPeriod)
        reporter.changed(changed)
        levels()
    }

    private fun checked(permits: Int): Limits {
        val limits = current.load()
        require(permits in 1..limits.limitForPeriod) {
            "A call asks for 1 to ${limits.limitForPeriod} permits of the rate limiter $name"
        }
        return limits
    }

    private fun levels() {
        reporter.levels(availablePermissions = { availablePermissions }, waitingCallers = waiting.load())
    }

    private fun nowNanos(): Long = started.elapsedNow().inWholeNanoseconds
}

private fun isValidTimeout(timeout: Duration): Boolean =
    !timeout.isNegative() && timeout <= RateLimiterConfig.HARD_TIMEOUT
