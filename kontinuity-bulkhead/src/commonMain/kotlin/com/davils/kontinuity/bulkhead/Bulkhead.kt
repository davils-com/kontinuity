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

package com.davils.kontinuity.bulkhead

import com.davils.kontinuity.bulkhead.internal.Gate
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.Stage
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * A semaphore bulkhead: it lets at most [BulkheadConfig.maxConcurrentCalls] calls run at once and lets further calls
 * wait for a place in a fair queue, as the semaphore bulkhead of resilience4j does.
 *
 * Create one with `bulkhead("payments") { maxConcurrentCalls = 10; maxWaitDuration = 500.milliseconds }` or take it
 * from a [BulkheadRegistry]. A call runs in the coroutine of its caller. When every permit is taken, a call waits for
 * at most [BulkheadConfig.maxWaitDuration] in a first-in, first-out queue of at most
 * [BulkheadConfig.maxWaitingCalls] callers; a call that finds the queue full, may not wait or waits too long is
 * refused with a [BulkheadFullException]. A permit that is given back goes to the longest waiting call, never to a
 * newcomer, so no caller starves.
 *
 * The queue is a list of suspended coroutines, not a semaphore of kotlinx.coroutines, so that [changeConfig] can
 * change the limits of a running bulkhead: a higher limit wakes waiting calls at once, a lower one lets running calls
 * finish and admits new ones only below it. A waiting caller that is cancelled leaves the queue at once; when its
 * cancellation races with the hand-over of a permit, the permit goes on to the next waiting call, so no permit is
 * ever lost. The cancellation of a caller is rethrown and counted as cancelled, never as a failure.
 *
 * A bulkhead is safe to use from many coroutines and threads at once and works the same on the JVM and on
 * WebAssembly; it starts no coroutine and no timer of its own. Reading [config] or [snapshot] changes nothing.
 *
 * @property name the name of the bulkhead.
 * @param config the first configuration.
 * @param environment the environment of the bulkhead.
 * @since 1.0.0
 */
public class Bulkhead internal constructor(
    override val name: InstanceName,
    config: BulkheadConfig,
    environment: Environment,
) : Stage {
    internal val gate: Gate = Gate(name, config, environment)

    override val kind: ResilienceKind get() = ResilienceKind.Bulkhead

    /**
     * The current configuration; [changeConfig] replaces it.
     *
     * @since 1.0.0
     */
    public val config: BulkheadConfig get() = gate.config

    /**
     * The events of the bulkhead, hot and without replay; a slow collector loses the oldest events.
     *
     * @since 1.0.0
     */
    public val events: Flow<BulkheadEvent> get() = gate.events

    /**
     * Returns the permits, the queue and the totals of the bulkhead at this moment.
     *
     * @return the snapshot.
     * @since 1.0.0
     */
    public fun snapshot(): BulkheadSnapshot = gate.snapshot()

    /**
     * Asks for a permit; never waits and never jumps the queue: while calls wait, a newcomer is refused.
     *
     * @return the permit, which must be released, or null if no permit is free; a refusal counts as rejected.
     * @since 1.0.0
     */
    public fun tryAcquirePermission(): BulkheadPermit? = gate.tryAcquire()

    /**
     * Asks for a permit and waits for at most [timeout] in the queue when none is free.
     *
     * A zero [timeout] never waits. The caller's cancellation while it waits removes it from the queue, gives back a
     * permit that was handed to it at the same moment and is rethrown.
     *
     * @param timeout how long to wait; [BulkheadConfig.maxWaitDuration] of the current configuration by default,
     *   from zero to [BulkheadConfig.HARD_WAIT].
     * @return the permit, which must be released.
     * @throws BulkheadFullException if the queue is full, the call may not wait or no permit became free in time;
     *   [BulkheadFullException.isQueueFull] tells which.
     * @throws IllegalArgumentException if [timeout] is negative or longer than [BulkheadConfig.HARD_WAIT].
     * @since 1.0.0
     */
    public suspend fun acquirePermission(timeout: Duration = config.maxWaitDuration): BulkheadPermit =
        gate.acquire(timeout)

    /**
     * Runs [block] while it holds a permit, waiting for one for at most [BulkheadConfig.maxWaitDuration], and gives
     * the permit back however [block] ends.
     *
     * @param T the type of the result.
     * @param block the protected call.
     * @return the result of [block].
     * @throws BulkheadFullException if the bulkhead refuses the call.
     * @throws Throwable what [block] throws.
     * @since 1.0.0
     */
    override suspend fun <T> execute(block: suspend () -> T): T {
        val permit = acquirePermission()
        try {
            return block()
        } finally {
            permit.release()
        }
    }

    /**
     * Replaces the configuration of the running bulkhead. A higher limit of concurrent calls hands permits to waiting
     * calls at once; a lower one takes no permit away but admits new calls only once the running ones fell below it.
     * A smaller queue keeps the calls that wait already and refuses newcomers until it has room. A new
     * [BulkheadConfig.maxWaitDuration] applies to the next calls.
     *
     * @param config the new configuration.
     * @since 1.0.0
     */
    public fun changeConfig(config: BulkheadConfig) {
        gate.resize(config)
    }

    /**
     * Shows the name and the limit of concurrent calls.
     *
     * @return the bulkhead as text.
     * @since 1.0.0
     */
    override fun toString(): String = "Bulkhead($name, maxConcurrentCalls=${config.maxConcurrentCalls})"
}
