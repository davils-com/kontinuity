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

package com.davils.kontinuity.policy

import com.davils.kontinuity.bulkhead.Bulkhead
import com.davils.kontinuity.bulkhead.DispatcherBulkhead
import com.davils.kontinuity.circuitbreaker.CircuitBreaker
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.Stage
import com.davils.kontinuity.hedge.Hedge
import com.davils.kontinuity.policy.internal.BreakerLayer
import com.davils.kontinuity.policy.internal.Layer
import com.davils.kontinuity.policy.internal.LimiterLayer
import com.davils.kontinuity.policy.internal.StageLayer
import com.davils.kontinuity.ratelimiter.RateLimiter
import com.davils.kontinuity.retry.Retry
import com.davils.kontinuity.timelimiter.TimeLimiter
import com.davils.kore.core.annotation.DavilsDsl
import com.davils.kore.core.dsl.ValidatingBuilder

/**
 * Builds a [Policy]: at most one component of each kind and any number of further stages.
 *
 * The order in which the components are declared does not matter; a policy always nests them in the order of
 * [Policy]. Further stages run inside every component, in the order they are declared.
 *
 * @param name the name of the policy.
 * @param environment the environment of the policy.
 * @since 1.0.0
 */
@DavilsDsl
public class PolicyBuilder internal constructor(
    private val name: InstanceName,
    private val environment: Environment,
) : ValidatingBuilder<Policy>() {
    private val slots: MutableMap<Slot, Layer> = linkedMapOf()
    private val stages: MutableList<Layer> = mutableListOf()

    internal val duplicates: MutableList<String> = mutableListOf()

    internal var rateLimiterPermits: Int = 1
        private set

    internal var rateLimiter: RateLimiter? = null
        private set

    override val subject: String get() = "policy"

    /**
     * Repeats failed calls through [retry]; it is the outermost stage.
     *
     * @param retry the retry.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun retry(retry: Retry) {
        put(Slot.Retry, StageLayer(retry))
    }

    /**
     * Protects every attempt with [breaker]; the duration of a call is measured after the waits of the rate limiter
     * and the bulkhead, so waiting never makes a call slow.
     *
     * @param breaker the circuit breaker.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun circuitBreaker(breaker: CircuitBreaker) {
        put(Slot.CircuitBreaker, BreakerLayer(breaker))
    }

    /**
     * Takes [permits] permits of [limiter] for every attempt.
     *
     * @param limiter the rate limiter.
     * @param permits the permits of one attempt, from 1 to the limit for period of [limiter]; 1 by default.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun rateLimiter(limiter: RateLimiter, permits: Int = 1) {
        put(Slot.RateLimiter, LimiterLayer(limiter, permits))
        rateLimiter = limiter
        rateLimiterPermits = permits
    }

    /**
     * Bounds the concurrent attempts with [bulkhead].
     *
     * @param bulkhead the semaphore bulkhead.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun bulkhead(bulkhead: Bulkhead) {
        put(Slot.Bulkhead, StageLayer(bulkhead))
    }

    /**
     * Runs every attempt on the bounded dispatcher of [bulkhead].
     *
     * @param bulkhead the dispatcher bulkhead.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun dispatcherBulkhead(bulkhead: DispatcherBulkhead) {
        put(Slot.DispatcherBulkhead, StageLayer(bulkhead))
    }

    /**
     * Limits the time of every attempt with [limiter]; the waits of the outer stages are not part of it.
     *
     * @param limiter the time limiter.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun timeLimiter(limiter: TimeLimiter) {
        put(Slot.TimeLimiter, StageLayer(limiter))
    }

    /**
     * Hedges every attempt with [hedge]; it is the innermost component.
     *
     * @param hedge the hedge.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun hedge(hedge: Hedge) {
        put(Slot.Hedge, StageLayer(hedge))
    }

    /**
     * Adds a further stage, which runs inside every component and inside the stages declared before it.
     *
     * @param stage the stage.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun stage(stage: Stage) {
        checkOpen()
        stages += StageLayer(stage)
    }

    internal fun layers(): List<Layer> = Slot.entries.mapNotNull { slot -> slots[slot] } + stages

    override fun assemble(): Policy = Policy(name = name, environment = environment, builder = this)

    private fun put(slot: Slot, layer: Layer) {
        checkOpen()
        if (slot in slots) duplicates += slot.name
        slots[slot] = layer
    }

    private enum class Slot {
        Retry,
        CircuitBreaker,
        RateLimiter,
        Bulkhead,
        DispatcherBulkhead,
        TimeLimiter,
        Hedge,
    }
}
