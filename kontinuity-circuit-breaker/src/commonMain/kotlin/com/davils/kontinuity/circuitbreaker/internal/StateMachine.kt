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

package com.davils.kontinuity.circuitbreaker.internal

import com.davils.kontinuity.circuitbreaker.CircuitBreakerConfig
import com.davils.kontinuity.circuitbreaker.CircuitBreakerSnapshot
import com.davils.kontinuity.circuitbreaker.CircuitState
import com.davils.kontinuity.circuitbreaker.TotalsSnapshot
import com.davils.kontinuity.circuitbreaker.WindowSnapshot
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.telemetry.Guard
import com.davils.kontinuity.core.telemetry.guard
import com.davils.kore.core.annotation.DavilsInternal
import kotlin.time.Duration
import kotlin.time.TimeSource

internal class StateMachine(
    private val config: CircuitBreakerConfig,
    environment: Environment,
    private val reporter: Reporter,
) {
    private val guard: Guard = guard()
    private val counts: Counts = Counts()
    private val phases: PhaseFactory = PhaseFactory(config, environment)
    private val thresholds: Thresholds = Thresholds(config)
    private val timer: TransitionTimer = TransitionTimer(config, environment)
    private var phase: Phase = phases.closed()
    private var generation: Long = 0L
    private var isRateExceeded: Boolean = false

    val timeSource: TimeSource.WithComparableMarks = environment.timeSource

    val state: CircuitState get() = guard.locked { phase.state }

    fun tryAcquire(): Admission {
        val changes = Changes()
        val admission = guard.locked {
            refresh(changes)
            admit()
        }
        reporter.changed(changes)
        if (admission.generation == null) reporter.notPermitted(admission.state)
        return admission
    }

    fun settle(token: Long, settlement: Settlement) {
        val changes = Changes()
        val totals = guard.locked {
            counts.count(settlement)
            if (token == generation) apply(settlement, changes)
            phase.window?.totals() ?: WindowTotals.EMPTY
        }
        reporter.settled(settlement)
        reporter.changed(changes)
        reporter.window(
            failureRate = totals.failureRate(minimumCalls()),
            slowCallRate = totals.slowCallRate(minimumCalls()),
            bufferedCalls = totals.calls,
        )
    }

    fun transitionTo(target: CircuitState): Boolean {
        val changes = Changes()
        val isMoved = guard.locked { moveIfDifferent(target, changes) }
        reporter.changed(changes)
        return isMoved
    }

    fun reset() {
        val changes = Changes()
        guard.locked {
            moveTo(phases.closed(), changes)
            changes.reset()
        }
        reporter.changed(changes)
    }

    fun snapshot(): CircuitBreakerSnapshot = guard.locked {
        val totals = phase.window?.totals() ?: WindowTotals.EMPTY
        val minimum = minimumCalls()
        val window = WindowSnapshot(
            failureRate = totals.failureRate(minimum),
            slowCallRate = totals.slowCallRate(minimum),
            bufferedCalls = totals.calls,
            failedCalls = totals.failures,
            slowCalls = totals.slow,
            slowFailedCalls = totals.slowFailures,
        )
        val sums = TotalsSnapshot(
            successful = counts.successful,
            failed = counts.failed,
            ignored = counts.ignored,
            released = counts.released,
            notPermitted = counts.notPermitted,
        )
        CircuitBreakerSnapshot(state = phase.state, window = window, totals = sums)
    }

    private fun admit(): Admission {
        val current = phase
        val isAdmitted = when (current) {
            is Phase.HalfOpen -> current.take()
            is Phase.Open, Phase.ForcedOpen -> false
            is Phase.Closed, is Phase.MetricsOnly, Phase.Disabled -> true
        }
        if (!isAdmitted) counts.refused()
        return Admission(generation = generation.takeIf { isAdmitted }, state = current.state)
    }

    private fun refresh(changes: Changes) {
        val current = phase
        if (current is Phase.Open && current.until.hasPassedNow()) enterHalfOpen(current, changes)
        if (current is Phase.HalfOpen && current.isExpired) moveTo(phases.afterHalfOpenTimeout(current), changes)
    }

    private fun wake(token: Long) {
        val changes = Changes()
        guard.locked { if (token == generation) expire(changes) }
        reporter.changed(changes)
    }

    private fun expire(changes: Changes) {
        val current = phase
        if (current is Phase.Open) enterHalfOpen(current, changes)
        if (current is Phase.HalfOpen) moveTo(phases.afterHalfOpenTimeout(current), changes)
    }

    private fun enterHalfOpen(current: Phase.Open, changes: Changes) {
        moveTo(phases.halfOpen(openings = current.openings, wait = current.wait), changes)
    }

    private fun apply(settlement: Settlement, changes: Changes) {
        val current = phase
        if (current is Phase.Closed) recordClosed(current.window, settlement, changes)
        if (current is Phase.MetricsOnly) recordMetricsOnly(current.window, settlement, changes)
        if (current is Phase.HalfOpen) recordTrial(current, settlement, changes)
    }

    private fun recordClosed(window: CallWindow, settlement: Settlement, changes: Changes) {
        val recorded = settlement as? Settlement.Recorded ?: return
        window.record(recorded.outcome)
        val isExceeded = thresholds.report(window.totals(), config.minimumNumberOfCalls, changes)
        if (recorded.isOpening || isExceeded) open(openings = 1, previousWait = Duration.ZERO, changes = changes)
    }

    private fun recordMetricsOnly(window: CallWindow, settlement: Settlement, changes: Changes) {
        val recorded = settlement as? Settlement.Recorded ?: return
        window.record(recorded.outcome)
        val totals = window.totals()
        val isExceeded = thresholds.isOver(totals, config.minimumNumberOfCalls)
        if (isExceeded && !isRateExceeded) thresholds.report(totals, config.minimumNumberOfCalls, changes)
        isRateExceeded = isExceeded
    }

    private fun recordTrial(trial: Phase.HalfOpen, settlement: Settlement, changes: Changes) {
        val recorded = settlement as? Settlement.Recorded
        if (recorded == null) {
            trial.giveBack()
            return
        }
        trial.record(recorded.outcome)
        if (recorded.isOpening) open(openings = trial.openings + 1, previousWait = trial.wait, changes = changes)
        if (!recorded.isOpening && trial.isComplete) concludeTrial(trial, changes)
    }

    private fun concludeTrial(trial: Phase.HalfOpen, changes: Changes) {
        val isExceeded = thresholds.report(trial.window.totals(), trial.trials, changes)
        if (isExceeded) open(openings = trial.openings + 1, previousWait = trial.wait, changes = changes)
        if (!isExceeded) moveTo(phases.closed(), changes)
    }

    private fun open(openings: Int, previousWait: Duration, changes: Changes) {
        moveTo(phases.open(openings = openings, previousWait = previousWait, window = phase.window), changes)
    }

    private fun moveIfDifferent(target: CircuitState, changes: Changes): Boolean {
        if (phase.state == target) return false
        moveTo(phases.forState(target, phase.window), changes)
        return true
    }

    private fun moveTo(next: Phase, changes: Changes) {
        val from = phase.state
        phase = next
        generation += 1L
        isRateExceeded = false
        timer.reschedule(next, generation, ::wake)
        changes.transition(from, next.state)
    }

    private fun minimumCalls(): Int {
        val trial = phase as? Phase.HalfOpen ?: return config.minimumNumberOfCalls
        return trial.trials
    }
}
