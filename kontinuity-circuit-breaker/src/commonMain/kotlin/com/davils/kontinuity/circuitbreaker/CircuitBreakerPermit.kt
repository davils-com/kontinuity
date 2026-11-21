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

package com.davils.kontinuity.circuitbreaker

import com.davils.kontinuity.circuitbreaker.internal.CallOutcome
import com.davils.kontinuity.circuitbreaker.internal.Settlement
import com.davils.kontinuity.circuitbreaker.internal.StateMachine
import com.davils.kontinuity.core.filter.FailureVerdict
import com.davils.kore.core.failure.contained
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration

private const val RESULT: String = "result"
private const val UNNAMED: String = "Throwable"

/**
 * The permission of a [CircuitBreaker] for one call, for callers that run the call themselves; [CircuitBreaker.execute]
 * uses it, too.
 *
 * A permit is settled exactly once, by [onSuccess], [onResult], [onFailure] or [release]; every later settlement is
 * ignored, so a permit can be released in a `finally` block after it was settled. The duration of the call is measured
 * from the moment the permit was issued, or from the last [restartClock]. A permit that is never settled keeps a trial
 * call of a half-open breaker occupied; settle every permit. A permit is safe to settle from any thread.
 *
 * @property state the state of the breaker when it issued the permit.
 * @since 1.0.0
 */
public class CircuitBreakerPermit internal constructor(
    private val machine: StateMachine,
    private val config: CircuitBreakerConfig,
    private val token: Long,
    public val state: CircuitState,
) {
    private val settled = AtomicBoolean(false)
    private val start = AtomicReference<ComparableTimeMark>(machine.timeSource.markNow())

    /**
     * Whether the permit was settled.
     *
     * @since 1.0.0
     */
    public val isSettled: Boolean get() = settled.load()

    /**
     * How long the call has taken so far.
     *
     * @since 1.0.0
     */
    public val elapsed: Duration get() = start.load().elapsedNow()

    /**
     * Starts measuring the duration of the call anew, such as after the call waited for a bulkhead or a rate limiter.
     *
     * @since 1.0.0
     */
    public fun restartClock() {
        start.store(machine.timeSource.markNow())
    }

    /**
     * Records the call as successful.
     *
     * @since 1.0.0
     */
    public fun onSuccess() {
        settle(recorded(isFailure = false, isOpening = false, failureType = RESULT))
    }

    /**
     * Records the call by its [result]: as failed when the result filter of the configuration accepts it, as
     * successful otherwise. A result that the opening filter accepts opens the breaker.
     *
     * @param result the result of the call.
     * @throws Throwable what a rule of the result filter throws, after recording the call as failed with it.
     * @since 1.0.0
     */
    public fun onResult(result: Any?) {
        val judged = contained { config.results.isFailure(result) }
        judged.exceptionOrNull()?.let { problem -> onFailure(problem) }
        val isFailure = judged.getOrThrow()
        val isOpening = contained { config.openingResults.isFailure(result) }.getOrDefault(false)
        settle(recorded(isFailure = isFailure, isOpening = isOpening, failureType = RESULT))
    }

    /**
     * Records the call by its [failure], as the failure filter of the configuration decides: as failed, as
     * successful or as ignored. A failure that the opening filter records opens the breaker.
     *
     * @param failure the failure of the call.
     * @since 1.0.0
     */
    public fun onFailure(failure: Throwable) {
        val isOpening = config.openingFailures?.verdictOf(failure) == FailureVerdict.Recorded
        val type = failure::class.simpleName ?: UNNAMED
        val settlement = when (config.failures.verdictOf(failure)) {
            FailureVerdict.Recorded -> recorded(isFailure = true, isOpening = isOpening, failureType = type)
            FailureVerdict.Passed -> recorded(isFailure = false, isOpening = isOpening, failureType = type)
            FailureVerdict.Ignored -> Settlement.Ignored(elapsed)
        }
        settle(settlement)
    }

    /**
     * Gives the permit back without an outcome, such as when the caller was cancelled; the call counts as nothing and
     * a half-open breaker may hand its trial to another call.
     *
     * @since 1.0.0
     */
    public fun release() {
        settle(Settlement.Released)
    }

    /**
     * Shows the state the permit was issued in and whether it is settled.
     *
     * @return the permit as text.
     * @since 1.0.0
     */
    override fun toString(): String = "CircuitBreakerPermit($state, settled=$isSettled)"

    private fun recorded(isFailure: Boolean, isOpening: Boolean, failureType: String): Settlement {
        val duration = elapsed
        val isSlow = duration > config.slowCallDurationThreshold
        return Settlement.Recorded(
            outcome = CallOutcome.of(isFailure = isFailure, isSlow = isSlow),
            duration = duration,
            isOpening = isOpening,
            failureType = failureType,
        )
    }

    private fun settle(settlement: Settlement) {
        if (settled.compareAndSet(expectedValue = false, newValue = true)) machine.settle(token, settlement)
    }
}
