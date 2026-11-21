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

package com.davils.kontinuity.hedge

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.Stage
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kontinuity.hedge.internal.HedgeMeters
import com.davils.kontinuity.hedge.internal.Ledger
import com.davils.kontinuity.hedge.internal.Race
import com.davils.kontinuity.hedge.internal.RaceResult
import com.davils.kontinuity.hedge.internal.Reporter
import com.davils.kore.core.annotation.DavilsInternal
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlin.coroutines.CoroutineContext
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

private const val FATAL: String = "fatal"

/**
 * A hedge: it starts a copy of a slow call after a delay and takes the first copy that succeeds, as the hedge of
 * resilience4j does, to cut the tail latency of idempotent calls.
 *
 * Create one with `hedge("search") { delay = percentileDelay(percentile = 0.99); maxHedgedAttempts = 2 }` or take
 * it from a [HedgeRegistry]. A call starts the protected block as its primary copy. Whenever the latest copy has run
 * for the delay of [HedgeConfig.delay] without a result, the hedge starts the next copy, up to
 * [HedgeConfig.maxHedgedAttempts] copies in addition to the primary. The first copy that succeeds wins and every
 * other copy is cancelled. A failure that [HedgeConfig.failures] records starts the next copy at once, if one is left,
 * instead of waiting for the delay; when every copy failed, the call throws the failure of the primary with the
 * failures of the other copies added as suppressed. A failure the filter ignores or passes, and every rejection of
 * another component of kontinuity, ends the call at once with that failure: a rejection is never hedged.
 *
 * Every copy runs as a child of the caller's coroutine inside its `coroutineScope`, on the caller's dispatcher; the
 * hedge starts no coroutine of its own outside the call, no thread and no timer. The cancellation of the caller
 * cancels every copy and passes through. Every copy runs through `attempted` of Kore, so the failure of one copy
 * never ends another, and a cancellation that does not come from the caller, such as the timeout of a callee, is a
 * failure of that copy. A copy that ignores its cancellation delays the end of the call until it returns.
 *
 * The block runs up to `maxHedgedAttempts + 1` times, possibly at the same time: hedge only calls that are
 * idempotent and safe to run in parallel. The extra load on the called service is bounded by
 * [HedgeConfig.HARD_HEDGED_ATTEMPTS] copies per call.
 *
 * A hedge is safe to use from many coroutines and threads at once. Every call is counted exactly once in [snapshot]
 * as succeeded by its primary, succeeded by a hedged copy, failed or cancelled. Reading [delay] or [snapshot] changes
 * nothing. A fatal error of the platform passes through and counts as a failure of the type `fatal`.
 *
 * @property name the name of the hedge.
 * @property config the configuration.
 * @param environment the environment of the hedge.
 * @since 1.0.0
 */
public class Hedge internal constructor(
    override val name: InstanceName,
    public val config: HedgeConfig,
    environment: Environment,
) : Stage {
    private val sink = EventSink(environment, HedgeEvent.TOPIC)
    private val timeSource: TimeSource.WithComparableMarks = environment.timeSource
    private val ledger: Ledger = Ledger(config.delay)
    private val reporter: Reporter = Reporter(
        name = name,
        ledger = ledger,
        sink = sink,
        meters = environment.meters?.let { registry -> HedgeMeters(registry, name) },
    )

    override val kind: ResilienceKind get() = ResilienceKind.Hedge

    /**
     * The delay the next call waits before it starts its first hedged copy; reading it changes nothing.
     *
     * @since 1.0.0
     */
    public val delay: Duration get() = ledger.delay

    /**
     * The events of the hedge, hot and without replay; a slow collector loses the oldest events.
     *
     * @since 1.0.0
     */
    public val events: Flow<HedgeEvent> get() = sink.events

    /**
     * Returns the totals and the current delay of the hedge at this moment.
     *
     * @return the snapshot.
     * @since 1.0.0
     */
    public fun snapshot(): HedgeSnapshot = ledger.snapshot()

    /**
     * Runs [block] as the primary copy and, while it is slow or fails, as hedged copies, and returns the result of
     * the first copy that succeeds.
     *
     * @param T the type of the result.
     * @param block the protected call; it runs up to [HedgeConfig.maxHedgedAttempts] + 1 times and must be safe to
     *   run in parallel.
     * @return the result of the first copy that succeeded.
     * @throws com.davils.kontinuity.core.CallRejectedException if a copy was refused by another component; the
     *   call ends at once.
     * @throws kotlinx.coroutines.CancellationException if the caller is cancelled; every copy is cancelled with it.
     * @throws Throwable the failure of the primary, with the failures of the other copies as suppressed, if every
     *   copy failed, or a failure the filter does not record, at once.
     * @since 1.0.0
     */
    override suspend fun <T> execute(block: suspend () -> T): T {
        val start = timeSource.markNow()
        val race = Race(
            config = config,
            pause = ledger.delay,
            onHedge = { attempt -> hedged(attempt, start) },
            block = block,
        )
        val result = raced(race, start)
        return settle(result, start.elapsedNow())
    }

    /**
     * Shows the name and the current delay.
     *
     * @return the hedge as text.
     * @since 1.0.0
     */
    override fun toString(): String = "Hedge($name, delay=$delay)"

    private fun hedged(attempt: Int, start: ComparableTimeMark) {
        reporter.hedgeStarted(attempt = attempt, after = start.elapsedNow())
    }

    private fun <T> settle(result: RaceResult<T>, duration: Duration): T = when (result) {
        is RaceResult.Won -> {
            reporter.won(result.attempt, duration)
            result.value
        }

        is RaceResult.Lost -> {
            reporter.failed(attempts = result.attempts, failure = result.failure, duration = duration)
            throw result.failure
        }
    }

    private suspend fun <T> raced(race: Race<T>, start: ComparableTimeMark): RaceResult<T> {
        val caller = currentCoroutineContext()
        var isCounted = false
        try {
            val result = race.run()
            isCounted = true
            return result
        } finally {
            unsettled(isCounted = isCounted, caller = caller, attempts = race.started, start = start)
        }
    }

    private fun unsettled(isCounted: Boolean, caller: CoroutineContext, attempts: Int, start: ComparableTimeMark) {
        if (isCounted) return
        val isCancelled = !caller.isActive
        if (isCancelled) reporter.cancelled()
        if (!isCancelled) reporter.failed(attempts = attempts, failureType = FATAL, duration = start.elapsedNow())
    }
}
