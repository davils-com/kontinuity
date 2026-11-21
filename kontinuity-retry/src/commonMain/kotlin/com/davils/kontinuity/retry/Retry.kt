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

package com.davils.kontinuity.retry

import com.davils.konfetti.core.bus.Entropy
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.Stage
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kontinuity.retry.internal.Counts
import com.davils.kontinuity.retry.internal.Decision
import com.davils.kontinuity.retry.internal.Reporter
import com.davils.kontinuity.retry.internal.RetryMeters
import com.davils.kontinuity.retry.internal.RetryRun
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.failure.attempted
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import kotlin.time.Duration

/**
 * A retry: it repeats a call that failed or returned a failing result, waits a backoff between the attempts and
 * gives up after [RetryConfig.maxAttempts] attempts, as the retry of resilience4j does.
 *
 * Create one with `retry("payments") { maxAttempts = 5; backoff = exponential(100.milliseconds) }` or take it from a
 * [RetryRegistry], and call it with `retry.execute { ... }` or `retry { ... }`. A failure that the failure filter
 * records is repeated; one that it passes or ignores, such as a rejection of another component of kontinuity, ends
 * the call at once. A result that the result filter fails is repeated; when the attempts run out, the call returns the
 * last result or, with [RetryConfig.isFailingAfterMaxAttempts], throws a [RetriesExhaustedException]. When the
 * attempts of a failing call run out, the failure of the last attempt is rethrown as it is. A [RetryBudget] may deny a
 * retry, which ends the call the same way.
 *
 * Every call is counted exactly once in [snapshot]. The cancellation of the caller stops the call at once, during an
 * attempt or a wait, and counts as cancelled; a cancellation that does not come from the caller, such as the timeout
 * of a callee, is a failure like any other. Fatal errors pass through. A retry is safe to use from many coroutines and
 * threads at once; it never chooses a dispatcher and waits with `delay`, so a test drives it with virtual time.
 *
 * @property name the name of the retry.
 * @property config the configuration.
 * @param environment the environment of the retry.
 * @since 1.0.0
 */
public class Retry internal constructor(
    override val name: InstanceName,
    public val config: RetryConfig,
    environment: Environment,
) : Stage {
    private val sink = EventSink(environment, RetryEvent.TOPIC)
    private val entropy: Entropy = environment.entropy
    private val counts: Counts = Counts()
    private val reporter: Reporter = Reporter(
        name = name,
        sink = sink,
        meters = environment.meters?.let { registry -> RetryMeters(registry, name) },
        counts = counts,
    )

    override val kind: ResilienceKind get() = ResilienceKind.Retry

    /**
     * The events of the retry, hot and without replay; a slow collector loses the oldest events.
     *
     * @since 1.0.0
     */
    public val events: Flow<RetryEvent> get() = sink.events

    /**
     * Returns the calls the retry counted since it was created; taking it changes nothing.
     *
     * @return the snapshot.
     * @since 1.0.0
     */
    public fun snapshot(): RetrySnapshot = counts.snapshot()

    /**
     * Runs [block] and repeats it as the configuration asks.
     *
     * @param T the type of the result.
     * @param block the protected call; it may run up to [RetryConfig.maxAttempts] times.
     * @return the first result the result filter accepts, or the last result when the attempts run out or the budget
     *   denies a retry.
     * @throws RetriesExhaustedException if every result failed and [RetryConfig.isFailingAfterMaxAttempts] is set.
     * @throws Throwable the failure of the last attempt, a failure the retry does not repeat, or what a rule of the
     *   result filter throws.
     * @since 1.0.0
     */
    override suspend fun <T> execute(block: suspend () -> T): T {
        val run = newRun()
        val context = currentCoroutineContext()
        try {
            return repeatUntilDone(run, block)
        } finally {
            run.settleOpen(isCancelled = !context.isActive)
        }
    }

    /**
     * Shows the name and the attempts.
     *
     * @return the retry as text.
     * @since 1.0.0
     */
    override fun toString(): String = "Retry($name, maxAttempts=${config.maxAttempts})"

    internal fun newRun(): RetryRun = RetryRun(config = config, entropy = entropy, reporter = reporter)

    private suspend fun <T> repeatUntilDone(run: RetryRun, block: suspend () -> T): T {
        while (true) {
            val outcome = attempted { block() }
            currentCoroutineContext().ensureActive()
            when (val decision = run.decide(outcome)) {
                is Decision.Again -> pause(decision.delay)
                Decision.Return -> return outcome.getOrThrow()
                is Decision.Fail -> throw decision.failure
            }
        }
    }

    private suspend fun pause(delay: Duration) {
        if (delay.isPositive()) delay(delay)
        yield()
    }
}
