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

package com.davils.kontinuity.circuitbreaker

import com.davils.kontinuity.circuitbreaker.internal.BreakerMeters
import com.davils.kontinuity.circuitbreaker.internal.Reporter
import com.davils.kontinuity.circuitbreaker.internal.StateMachine
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.Stage
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.failure.attempted
import kotlinx.coroutines.flow.Flow

/**
 * A circuit breaker: it lets calls through while they mostly succeed, refuses them for a while when too many fail or
 * are slow, and then tries a few calls before it lets all through again, as the circuit breaker of resilience4j does.
 *
 * Create one with `circuitBreaker("payments") { failureRateThreshold = 30.0 }` or take it from a
 * [CircuitBreakerRegistry]. A closed breaker records the outcome of every call in its [SlidingWindow]; once the
 * window holds [CircuitBreakerConfig.minimumNumberOfCalls] calls and the failure rate or the rate of slow calls
 * reaches its threshold, the breaker opens and refuses calls with a [CallNotPermittedException]. After the wait of
 * [CircuitBreakerConfig.waitInOpenState] the next call moves it to half-open, or a timer on the scope of the
 * environment does with [CircuitBreakerConfig.isAutomaticTransitionEnabled]; the trial calls of a half-open breaker
 * then close it or open it again. Rejections of other components of kontinuity never count as failures.
 *
 * A breaker is safe to use from many coroutines and threads at once. Its state changes only on a call, on a timer
 * of the caller's scope or by hand; reading [state] or [snapshot] changes nothing. The cancellation of a caller
 * releases its permit without an outcome. An outcome of a call that was permitted before the last transition counts in
 * the totals of [snapshot] but not in the window of the new state.
 *
 * @property name the name of the breaker.
 * @property config the configuration.
 * @param environment the environment of the breaker.
 * @since 1.0.0
 */
public class CircuitBreaker internal constructor(
    override val name: InstanceName,
    public val config: CircuitBreakerConfig,
    environment: Environment,
) : Stage {
    private val sink = EventSink(environment, CircuitBreakerEvent.TOPIC)
    private val machine: StateMachine

    init {
        val isScheduled = config.isAutomaticTransitionEnabled
        require(!isScheduled || environment.scope != null) {
            "The circuit breaker $name needs the scope of its environment for its automatic transition"
        }
        val meters = environment.meters?.let { registry -> BreakerMeters(registry, name) }
        machine = StateMachine(config = config, environment = environment, reporter = Reporter(name, sink, meters))
    }

    override val kind: ResilienceKind get() = ResilienceKind.CircuitBreaker

    /**
     * The state; reading it changes nothing.
     *
     * @since 1.0.0
     */
    public val state: CircuitState get() = machine.state

    /**
     * The events of the breaker, hot and without replay; a slow collector loses the oldest events.
     *
     * @since 1.0.0
     */
    public val events: Flow<CircuitBreakerEvent> get() = sink.events

    /**
     * Returns the state, the window and the totals of the breaker at this moment.
     *
     * @return the snapshot.
     * @since 1.0.0
     */
    public fun snapshot(): CircuitBreakerSnapshot = machine.snapshot()

    /**
     * Asks for the permission to run one call; never waits.
     *
     * @return the permit, which must be settled, or null if the breaker refuses the call.
     * @since 1.0.0
     */
    public fun tryAcquirePermission(): CircuitBreakerPermit? {
        val admission = machine.tryAcquire()
        val token = admission.generation ?: return null
        return CircuitBreakerPermit(machine = machine, config = config, token = token, state = admission.state)
    }

    /**
     * Asks for the permission to run one call; never waits.
     *
     * @return the permit, which must be settled.
     * @throws CallNotPermittedException if the breaker refuses the call.
     * @since 1.0.0
     */
    public fun acquirePermission(): CircuitBreakerPermit {
        val admission = machine.tryAcquire()
        val token = admission.generation ?: throw CallNotPermittedException(name, admission.state)
        return CircuitBreakerPermit(machine = machine, config = config, token = token, state = admission.state)
    }

    /**
     * Runs [block] if the breaker permits it and records its outcome.
     *
     * @param T the type of the result.
     * @param block the protected call.
     * @return the result of [block].
     * @throws CallNotPermittedException if the breaker refuses the call.
     * @throws Throwable what [block] throws, or what a rule of the result filter throws.
     * @since 1.0.0
     */
    override suspend fun <T> execute(block: suspend () -> T): T = executeTimed { _ -> block() }

    /**
     * Runs [block] like [execute] and hands it a function that starts measuring the duration of the call anew, for a
     * policy that waits for a bulkhead or a rate limiter inside the breaker.
     *
     * @param T the type of the result.
     * @param block the protected call, which receives the function that restarts the clock.
     * @return the result of [block].
     * @throws CallNotPermittedException if the breaker refuses the call.
     * @throws Throwable what [block] throws, or what a rule of the result filter throws.
     * @since 1.0.0
     */
    @DavilsInternal
    public suspend fun <T> executeTimed(block: suspend (restartClock: () -> Unit) -> T): T {
        val permit = acquirePermission()
        try {
            val result = attempted { block(permit::restartClock) }
            result.onFailure { failure -> permit.onFailure(failure) }
            val value = result.getOrThrow()
            permit.onResult(value)
            return value
        } finally {
            permit.release()
        }
    }

    /**
     * Moves the breaker to [target] by hand; a closed or metrics-only breaker starts with an empty window.
     *
     * @param target the new state.
     * @return true if the state changed, false if the breaker was in [target] already.
     * @since 1.0.0
     */
    public fun transitionTo(target: CircuitState): Boolean = machine.transitionTo(target)

    /**
     * Moves the breaker to a closed state with an empty window; the totals of [snapshot] keep counting.
     *
     * @since 1.0.0
     */
    public fun reset() {
        machine.reset()
    }

    /**
     * Shows the name and the state.
     *
     * @return the breaker as text.
     * @since 1.0.0
     */
    override fun toString(): String = "CircuitBreaker($name, $state)"
}
