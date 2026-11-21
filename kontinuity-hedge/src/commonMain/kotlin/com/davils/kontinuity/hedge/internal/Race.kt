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

package com.davils.kontinuity.hedge.internal

import com.davils.kontinuity.core.filter.FailureVerdict
import com.davils.kontinuity.hedge.HedgeConfig
import com.davils.kore.core.failure.attempted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

internal class Race<T>(
    private val config: HedgeConfig,
    private val pause: Duration,
    private val onHedge: (attempt: Int) -> Unit,
    private val block: suspend () -> T,
) {
    private val copies: Int = config.maxHedgedAttempts + 1
    private val signals: Channel<Signal<T>> = Channel(capacity = copies * 2)
    private val failures: MutableList<IndexedValue<Throwable>> = mutableListOf()
    private var finished: Int = 0

    var started: Int = 0
        private set

    suspend fun run(): RaceResult<T> = coroutineScope {
        start(this)
        var result: RaceResult<T>? = null
        while (result == null) result = handle(scope = this, signal = signals.receive())
        coroutineContext.cancelChildren()
        result
    }

    private fun start(scope: CoroutineScope) {
        val attempt = started
        started += 1
        if (attempt > 0) onHedge(attempt)
        scope.launch { signals.send(Signal.Finished(attempt, attempted { block() })) }
        if (started < copies) scope.launch { due(attempt) }
    }

    private suspend fun due(attempt: Int) {
        delay(pause)
        signals.send(Signal.Due(attempt))
    }

    private fun handle(scope: CoroutineScope, signal: Signal<T>): RaceResult<T>? = when (signal) {
        is Signal.Due -> {
            if (signal.attempt == started - 1) start(scope)
            null
        }

        is Signal.Finished -> {
            finished(scope, signal)
        }
    }

    private fun finished(scope: CoroutineScope, signal: Signal.Finished<T>): RaceResult<T>? {
        finished += 1
        val outcome = signal.outcome
        val failure = outcome.exceptionOrNull() ?: return RaceResult.Won(outcome.getOrThrow(), signal.attempt)
        return failed(scope = scope, attempt = signal.attempt, failure = failure)
    }

    private fun failed(scope: CoroutineScope, attempt: Int, failure: Throwable): RaceResult<T>? {
        val verdict = config.failures.verdictOf(failure)
        if (verdict != FailureVerdict.Recorded) return RaceResult.Lost(failure, started)
        failures += IndexedValue(attempt, failure)
        if (started < copies) start(scope)
        return lostIfDone()
    }

    private fun lostIfDone(): RaceResult<T>? {
        if (finished < started) return null
        return RaceResult.Lost(combined(), started)
    }

    private fun combined(): Throwable {
        val ordered = failures.sortedBy { failure -> failure.index }
        val primary = ordered.first().value
        ordered.drop(1).forEach { other -> suppress(primary, other.value) }
        return primary
    }

    private fun suppress(primary: Throwable, other: Throwable) {
        val known = primary.suppressedExceptions
        val isRoom = known.size < HedgeConfig.HARD_HEDGED_ATTEMPTS
        val isNew = other !== primary && known.none { suppressed -> suppressed === other }
        if (isRoom && isNew) primary.addSuppressed(other)
    }
}
