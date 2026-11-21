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

package com.davils.kontinuity.timelimiter

import com.davils.kontinuity.core.CallTimeoutException
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.Stage
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kontinuity.timelimiter.internal.Completion
import com.davils.kontinuity.timelimiter.internal.Limit
import com.davils.kontinuity.timelimiter.internal.LimiterMeters
import com.davils.kontinuity.timelimiter.internal.Reporter
import com.davils.kontinuity.timelimiter.internal.limitOf
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.failure.attempted
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * A time limiter: it ends a call that takes longer than its [TimeLimiterConfig.timeout] and throws a
 * [CallTimeoutException] in its place, as the `TimeLimiter` of resilience4j does.
 *
 * Create one with `timeLimiter("payments") { timeout = 2.seconds }` or take it from a [TimeLimiterRegistry]. The
 * [TimeLimiterConfig.strategy] decides how a call ends at its timeout: [TimeoutStrategy.Hard] cancels it in the
 * coroutine of its caller, [TimeoutStrategy.Soft] stops waiting for it and leaves it detached on the scope of the
 * environment, bounded by [TimeoutStrategy.Soft.maxDetached]. A soft limiter needs that scope; kontinuity never
 * starts a coroutine, a thread or a timer of its own.
 *
 * Every call ends in exactly one of five ways, which [snapshot] counts and the meters record:
 *
 * - successful: it returned within its timeout;
 * - failed: it threw within its timeout. A cancellation that does not come from the caller, such as the
 *   `TimeoutCancellationException` of a `withTimeout` inside the call, is a failure, thrown as the
 *   `IllegalStateException` that `attempted` of Kore wraps it in, so that it never ends the caller silently;
 * - timed out: it ran out of time. A timeout is a failure of the call, never a rejection, so a retry repeats it and a
 *   circuit breaker records it;
 * - cancelled: its caller was cancelled before it ended. The cancellation is thrown on at once and never counted as a
 *   failure; a fatal error of the platform that passes through is counted the same way;
 * - rejected: a soft limiter refused it with a [DetachedLimitExceededException] before it ran.
 *
 * A limiter is safe to use from many coroutines and threads at once and holds no lock while a call runs. Reading
 * [snapshot] changes nothing.
 *
 * @property name the name of the limiter.
 * @property config the configuration.
 * @param environment the environment of the limiter.
 * @throws IllegalArgumentException if the strategy is soft and the environment has no scope.
 * @since 1.0.0
 */
public class TimeLimiter internal constructor(
    override val name: InstanceName,
    public val config: TimeLimiterConfig,
    environment: Environment,
) : Stage {
    private val sink = EventSink(environment, TimeLimiterEvent.TOPIC)
    private val timeSource: TimeSource.WithComparableMarks = environment.timeSource
    private val reporter: Reporter
    private val limit: Limit

    init {
        val meters = environment.meters?.let { registry -> LimiterMeters(registry, name) }
        reporter = Reporter(name, sink, meters)
        limit = limitOf(name = name, config = config, environment = environment, reporter = reporter)
        reporter.detached(limit.detachedInFlight)
    }

    override val kind: ResilienceKind get() = ResilienceKind.TimeLimiter

    /**
     * The events of the limiter, hot and without replay; a slow collector loses the oldest events.
     *
     * @since 1.0.0
     */
    public val events: Flow<TimeLimiterEvent> get() = sink.events

    /**
     * Returns the totals of the limiter and its detached calls at this moment.
     *
     * @return the snapshot.
     * @since 1.0.0
     */
    public fun snapshot(): TimeLimiterSnapshot = reporter.snapshot(limit.detachedInFlight)

    /**
     * Runs [block] within the timeout and returns its result.
     *
     * @param T the type of the result.
     * @param block the protected call.
     * @return the result of [block].
     * @throws CallTimeoutException if [block] runs out of time.
     * @throws DetachedLimitExceededException if a soft limiter holds as many detached calls as it may.
     * @throws IllegalStateException if [block] ends in a cancellation that does not come from the caller, such as the
     *   timeout of a callee, or if the scope of a soft limiter was cancelled; the cancellation is its cause.
     * @throws Throwable what [block] throws.
     * @since 1.0.0
     */
    override suspend fun <T> execute(block: suspend () -> T): T {
        val finished = finishedOrNull(block) ?: throw CallTimeoutException(name, config.timeout)
        return finished.value
    }

    /**
     * Runs [block] within the timeout and returns its result, or the value of [onTimeout] when it runs out of time,
     * like the fallback of resilience4j for a `TimeoutException`.
     *
     * The call counts as timed out either way. [onTimeout] is foreign code and runs in the coroutine of the caller:
     * when it throws, its exception is thrown with the [CallTimeoutException] added as suppressed, as the handlers of
     * a core `Fallback` do. It handles only the timeout; every other failure and every rejection is thrown as it is.
     *
     * @param T the type of the result.
     * @param onTimeout returns the value in place of a call that ran out of time.
     * @param block the protected call.
     * @return the result of [block] or of [onTimeout].
     * @throws DetachedLimitExceededException if a soft limiter holds as many detached calls as it may.
     * @throws IllegalStateException if [block] ends in a cancellation that does not come from the caller.
     * @throws Throwable what [block] or [onTimeout] throws.
     * @since 1.0.0
     */
    public suspend fun <T> execute(onTimeout: suspend (CallTimeoutException) -> T, block: suspend () -> T): T {
        val finished = finishedOrNull(block) ?: return recovered(onTimeout)
        return finished.value
    }

    /**
     * Shows the name, the timeout and the strategy.
     *
     * @return the limiter as text.
     * @since 1.0.0
     */
    override fun toString(): String = "TimeLimiter($name, ${config.timeout}, ${config.strategy})"

    private suspend fun <T> finishedOrNull(block: suspend () -> T): Completion.Finished<T>? {
        val started = timeSource.markNow()
        var isSettled = false
        try {
            val result = attempted { limit.run(block) }
            isSettled = true
            val isCallerActive = currentCoroutineContext().isActive
            return settled(result = result, started = started, isCallerActive = isCallerActive)
        } finally {
            if (!isSettled) reporter.cancelled(started.elapsedNow())
        }
    }

    private fun <T> settled(
        result: Result<Completion<T>>,
        started: ComparableTimeMark,
        isCallerActive: Boolean,
    ): Completion.Finished<T>? {
        val elapsed = started.elapsedNow()
        val failure = result.exceptionOrNull()
        if (failure != null) throw ended(failure = failure, elapsed = elapsed, isCallerActive = isCallerActive)
        return when (val completion = result.getOrThrow()) {
            is Completion.Finished -> succeeded(completion, elapsed)
            Completion.TimedOut -> timedOut(elapsed)
            is Completion.Rejected -> throw rejected(completion.maxDetached)
        }
    }

    private fun <T> succeeded(completion: Completion.Finished<T>, elapsed: Duration): Completion.Finished<T> {
        reporter.succeeded(elapsed)
        return completion
    }

    private fun timedOut(elapsed: Duration): Nothing? {
        reporter.timedOut(elapsed, config.timeout)
        return null
    }

    private fun rejected(maxDetached: Int): DetachedLimitExceededException {
        reporter.rejected(maxDetached)
        return DetachedLimitExceededException(name, maxDetached)
    }

    private fun ended(failure: Throwable, elapsed: Duration, isCallerActive: Boolean): Throwable {
        if (isCallerActive) reporter.failed(elapsed, failure)
        if (!isCallerActive) reporter.cancelled(elapsed)
        return failure
    }

    private suspend fun <T> recovered(onTimeout: suspend (CallTimeoutException) -> T): T {
        val timeout = CallTimeoutException(name, config.timeout)
        val handled = attempted { onTimeout(timeout) }
        val problem = handled.exceptionOrNull() ?: return handled.getOrThrow()
        if (problem !== timeout) problem.addSuppressed(timeout)
        throw problem
    }
}
