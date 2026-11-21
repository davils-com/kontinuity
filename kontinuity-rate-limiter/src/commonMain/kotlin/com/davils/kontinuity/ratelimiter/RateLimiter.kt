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

package com.davils.kontinuity.ratelimiter

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.Stage
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kontinuity.ratelimiter.internal.Gate
import com.davils.kontinuity.ratelimiter.internal.LimiterMeters
import com.davils.kontinuity.ratelimiter.internal.Reporter
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.failure.attempted
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * A rate limiter: it lets at most [RateLimiterConfig.limitForPeriod] permits through per
 * [RateLimiterConfig.limitRefreshPeriod] and makes callers wait for free permits up to their timeout, as the rate
 * limiter of resilience4j does.
 *
 * Create one with `rateLimiter("search") { limitForPeriod = 100; limitRefreshPeriod = 1.seconds }` or take it from a
 * [RateLimiterRegistry]. A call to [execute] takes one permit, [execute] with a count takes several. A caller whose
 * permits are free now runs at once; a caller whose permits are free within its timeout reserves them and suspends
 * until they are due; any other caller, and every caller that would have to wait while
 * [RateLimiterConfig.maxWaitingCallers] callers wait already, is refused at once with a
 * [RequestNotPermittedException]. The [RateLimiterConfig.algorithm] decides how the permits are counted.
 *
 * Every wait is bounded: by the timeout, which is at most [RateLimiterConfig.HARD_TIMEOUT], and by the queue of
 * waiting callers. [tryAcquire] never waits. A caller cancelled while it waits gives its reservation back under
 * `NonCancellable`, so later callers can use the permits, as far as the algorithm can take it back without letting
 * more permits through than the limit: the sliding window takes back every reservation that is not due yet, the fixed
 * window one in the latest reserved period, and the token bucket only the latest reservation. A reservation that
 * cannot be taken back stays spent, which errs on the side of fewer permits.
 *
 * Every request for permits is counted exactly once, as acquired, rejected or cancelled, in [snapshot], the events
 * and the meters. A limiter is safe to use from many coroutines and threads at once; the fixed window and the token
 * bucket are lock-free, the sliding window holds a short lock. Reading [availablePermissions], [waitingCallers] or
 * [snapshot] changes nothing a caller can observe. The limiter measures time with the time source of its
 * environment, starting when it is created, and starts no coroutine, thread or timer of its own.
 *
 * @property name the name of the limiter.
 * @property config the configuration the limiter was created with; [limitForPeriod] and [timeout] hold the values
 *   changed at runtime.
 * @param environment the environment of the limiter.
 * @since 1.0.0
 */
public class RateLimiter internal constructor(
    override val name: InstanceName,
    public val config: RateLimiterConfig,
    environment: Environment,
) : Stage {
    private val sink = EventSink(environment, RateLimiterEvent.TOPIC)
    internal val gate: Gate

    init {
        val meters = environment.meters?.let { registry -> LimiterMeters(registry, name) }
        gate = Gate(
            name = name,
            config = config,
            reporter = Reporter(name, sink, meters),
            timeSource = environment.timeSource,
        )
    }

    override val kind: ResilienceKind get() = ResilienceKind.RateLimiter

    /**
     * The events of the limiter, hot and without replay; a slow collector loses the oldest events.
     *
     * @since 1.0.0
     */
    public val events: Flow<RateLimiterEvent> get() = sink.events

    /**
     * The limit per period now: [RateLimiterConfig.limitForPeriod] until [changeLimitForPeriod] changes it.
     *
     * @since 1.0.0
     */
    public val limitForPeriod: Int get() = gate.limits.limitForPeriod

    /**
     * The timeout of callers now: [RateLimiterConfig.timeout] until [changeTimeout] changes it.
     *
     * @since 1.0.0
     */
    public val timeout: Duration get() = gate.limits.timeout

    /**
     * The permits a caller could take at once now; zero while callers wait for permits of a later period.
     *
     * @since 1.0.0
     */
    public val availablePermissions: Int get() = gate.availablePermissions

    /**
     * The callers that wait for their permits now, at most [RateLimiterConfig.maxWaitingCallers].
     *
     * @since 1.0.0
     */
    public val waitingCallers: Int get() = gate.waitingCallers

    /**
     * Returns the permits, the waiting callers, the limits and the totals of the limiter at this moment.
     *
     * @return the snapshot.
     * @since 1.0.0
     */
    public fun snapshot(): RateLimiterSnapshot = gate.snapshot()

    /**
     * Takes one permit and runs [block].
     *
     * @param T the type of the result.
     * @param block the protected call.
     * @return the result of [block].
     * @throws RequestNotPermittedException if the permit is not free within the timeout or the queue is full.
     * @throws Throwable what [block] throws, or what a rule of [RateLimiterConfig.drainOnResults] throws.
     * @since 1.0.0
     */
    override suspend fun <T> execute(block: suspend () -> T): T = execute(permits = 1, block = block)

    /**
     * Takes [permits] permits, waiting for them up to the timeout, and runs [block]; a failure that
     * [RateLimiterConfig.drainOnFailures] records or a result that [RateLimiterConfig.drainOnResults] accepts drains
     * the remaining permits of the current period.
     *
     * The permits are spent once [block] starts, whatever its outcome. The cancellation of the caller is rethrown at
     * once; while it waits, it gives the reservation back.
     *
     * @param T the type of the result.
     * @param permits how many permits the call needs, from 1 to [limitForPeriod].
     * @param block the protected call.
     * @return the result of [block].
     * @throws IllegalArgumentException if [permits] is out of its bounds.
     * @throws RequestNotPermittedException if the permits are not free within the timeout or the queue is full.
     * @throws Throwable what [block] throws, or what a rule of [RateLimiterConfig.drainOnResults] throws.
     * @since 1.0.0
     */
    public suspend fun <T> execute(permits: Int, block: suspend () -> T): T {
        acquire(permits)
        val result = attempted { block() }
        val value = result.getOrElse { failure -> throw gate.failed(failure) }
        gate.succeeded(value)
        return value
    }

    /**
     * Takes [permits] permits, suspending until they are due but at most [timeout].
     *
     * The caller's permits are reserved at once, in the order of the requests, so that a caller that waits is never
     * overtaken by a later one. A caller that would have to wait longer than [timeout], or would have to wait while
     * [RateLimiterConfig.maxWaitingCallers] callers wait already, is refused at once without waiting. The
     * cancellation of the caller while it waits gives the reservation back and is rethrown.
     *
     * @param permits how many permits to take, from 1 to [limitForPeriod]; one by default.
     * @param timeout how long to wait at most, from zero to [RateLimiterConfig.HARD_TIMEOUT]; [timeout] of the
     *   limiter by default.
     * @throws IllegalArgumentException if [permits] or [timeout] is out of its bounds.
     * @throws RequestNotPermittedException if the permits are not free within [timeout] or the queue is full.
     * @since 1.0.0
     */
    public suspend fun acquire(permits: Int = 1, timeout: Duration = this.timeout) {
        gate.acquire(permits, timeout)
    }

    /**
     * Takes [permits] permits if they are free now; never waits, not even while other callers wait.
     *
     * @param permits how many permits to take, from 1 to [limitForPeriod]; one by default.
     * @return true if the permits were taken, false if the limiter refused them.
     * @throws IllegalArgumentException if [permits] is out of its bounds.
     * @since 1.0.0
     */
    public fun tryAcquire(permits: Int = 1): Boolean = gate.tryAcquire(permits)

    /**
     * Reserves [permits] permits and returns how long the caller must wait before it uses them, for callers that
     * wait on their own, like `reservePermission` of resilience4j.
     *
     * The reservation is counted as acquired at once and cannot be given back; the limiter does not count the
     * caller as waiting. A caller that would have to wait longer than [timeout], or while
     * [RateLimiterConfig.maxWaitingCallers] callers wait already, gets nothing.
     *
     * @param permits how many permits to reserve, from 1 to [limitForPeriod]; one by default.
     * @return the wait before the permits are due, zero if they are free now, or null if the limiter refused them.
     * @throws IllegalArgumentException if [permits] is out of its bounds.
     * @since 1.0.0
     */
    public fun reservePermission(permits: Int = 1): Duration? = gate.reservePermission(permits)

    /**
     * Changes the limit per period at runtime, like `changeLimitForPeriod` of resilience4j. The fixed window keeps the
     * permits of the current period and refills with the new limit from the next period on; the sliding window counts
     * with it from the next request on; the token bucket keeps the permits it owes and refills at the new rate from
     * now on. Callers that wait keep their reservations.
     *
     * @param limit the new limit, from 1 to [RateLimiterConfig.HARD_LIMIT_FOR_PERIOD]; for a token bucket at most its
     *   burst and large enough that the bucket refills within [RateLimiterAlgorithm.HARD_REFILL].
     * @throws IllegalArgumentException if [limit] is out of its bounds.
     * @since 1.0.0
     */
    public fun changeLimitForPeriod(limit: Int) {
        gate.changeLimitForPeriod(limit)
    }

    /**
     * Changes the timeout of callers at runtime; callers that wait already keep their wait.
     *
     * @param timeout the new timeout, from zero to [RateLimiterConfig.HARD_TIMEOUT].
     * @throws IllegalArgumentException if [timeout] is out of its bounds.
     * @since 1.0.0
     */
    public fun changeTimeout(timeout: Duration) {
        gate.changeTimeout(timeout)
    }

    /**
     * Drains the permits that are still free in the current period, so that the next caller waits for the next
     * period; reservations of waiting callers stay valid.
     *
     * @return how many free permits were drained.
     * @since 1.0.0
     */
    public fun drain(): Int = gate.drain()

    /**
     * Shows the name, the algorithm and the limit.
     *
     * @return the limiter as text.
     * @since 1.0.0
     */
    override fun toString(): String = "RateLimiter($name, ${config.algorithm}, $limitForPeriod per " +
        "${config.limitRefreshPeriod})"
}
