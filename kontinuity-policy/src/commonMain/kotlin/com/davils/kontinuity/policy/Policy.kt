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

package com.davils.kontinuity.policy

import com.davils.kollect.core.trace.traced
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.Instance
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.Stage
import com.davils.kontinuity.core.rejectionOrNull
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kontinuity.policy.internal.Layer
import com.davils.kontinuity.policy.internal.PolicyCall
import com.davils.kontinuity.policy.internal.PolicyCounts
import com.davils.kontinuity.policy.internal.PolicyMeters
import com.davils.kontinuity.policy.internal.PolicyOutcome
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.collection.immutableList
import com.davils.kore.core.concurrency.Guard
import com.davils.kore.core.concurrency.guard
import com.davils.kore.core.failure.attempted
import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.validateOrThrow
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

private const val SUBJECT: String = "policy"
private const val OPERATION: String = "kontinuity.policy"
private const val ATTRIBUTE: String = "kontinuity.policy.name"
private const val UNNAMED: String = "Throwable"

/**
 * Composes the components of kontinuity around a call, in a fixed order from the outside in:
 *
 * **Retry → CircuitBreaker → RateLimiter → Bulkhead → DispatcherBulkhead → TimeLimiter → Hedge → stages → call**
 *
 * Create one with `resilience("checkout") { retry(retry); circuitBreaker(breaker); timeLimiter(limiter) }`. A retry
 * repeats the whole chain, so every attempt asks the circuit breaker, the rate limiter and the bulkhead again. The
 * bulkhead stands outside the time limiter, so the timeout bounds the call and never the wait for a slot, and the
 * circuit breaker measures the duration of a call only after the waits of the rate limiter and the bulkhead, so a
 * wait never makes a call slow. A fallback and a cache go around a policy: `policy.execute(fallback) { }` and
 * `policy.cached(cache)`.
 *
 * Every call is counted exactly once as successful, failed, rejected by a stage or cancelled, reported as a
 * [PolicyEvent], recorded in the meters `kontinuity.policy.*` and, with the tracer of the environment, wrapped in a
 * span `kontinuity.policy`. A policy is safe to use from many coroutines and threads at once.
 *
 * @property name the name of the policy.
 * @param environment the environment of the policy.
 * @param builder the builder that declared the stages.
 * @since 1.0.0
 */
public class Policy internal constructor(
    override val name: InstanceName,
    environment: Environment,
    builder: PolicyBuilder,
) : Stage, Validatable {
    private val layers: List<Layer> = builder.layers()
    private val duplicates: List<String> = builder.duplicates.toList()
    private val limiterPermits: Int = builder.rateLimiterPermits
    private val limit: Int? = builder.rateLimiter?.limitForPeriod
    private val timeSource = environment.timeSource
    private val tracer = environment.tracer
    private val sink = EventSink(environment, PolicyEvent.TOPIC)
    private val meters = environment.meters?.let { registry -> PolicyMeters(registry, name) }
    private val guard: Guard = guard()
    private val counts = PolicyCounts()

    init {
        validateOrThrow(SUBJECT)
    }

    override val kind: ResilienceKind get() = ResilienceKind.Policy

    /**
     * The instances of the policy, from the outermost to the innermost.
     *
     * @since 1.0.0
     */
    public val stages: List<Instance> = layers.map { layer -> layer.instance }.immutableList()

    /**
     * The events of the policy, hot and without replay; a slow collector loses the oldest events.
     *
     * @since 1.0.0
     */
    public val events: Flow<PolicyEvent> get() = sink.events

    /**
     * Runs [block] through every stage of the policy.
     *
     * @param T the type of the result.
     * @param block the protected call.
     * @return the result of [block].
     * @throws com.davils.kontinuity.core.CallRejectedException if a stage refuses the call.
     * @throws Throwable what [block] or a stage throws.
     * @since 1.0.0
     */
    override suspend fun <T> execute(block: suspend () -> T): T = tracer.traced(OPERATION) { span ->
        span.attribute(key = ATTRIBUTE, value = name.value)
        measured(block)
    }

    /**
     * Returns how many calls the policy counted.
     *
     * @return the snapshot.
     * @since 1.0.0
     */
    public fun snapshot(): PolicySnapshot = guard.locked {
        PolicySnapshot(
            successful = counts.successful,
            failed = counts.failed,
            rejected = counts.rejected,
            cancelled = counts.cancelled,
        )
    }

    /**
     * Checks that the policy has a stage, no component twice, no instance twice and permits within the limit of its
     * rate limiter.
     *
     * @receiver the validation that collects the violations.
     * @since 1.0.0
     */
    override fun ValidationScope.validate() {
        if (layers.isEmpty()) fail("has no stage")
        if (duplicates.isNotEmpty()) fail("declares a kind of component more than once")
        val instances = layers.map { layer -> layer.instance }
        val isRepeated = instances.withIndex().any { (index, instance) ->
            instances.take(index).any { earlier -> earlier === instance }
        }
        if (isRepeated) fail("holds an instance more than once")
        val isWithinLimit = limit == null || limiterPermits in 1..limit
        if (!isWithinLimit) fail("takes permits outside of the limit for period of its rate limiter")
    }

    /**
     * Shows the name and the stages.
     *
     * @return the policy as text.
     * @since 1.0.0
     */
    override fun toString(): String =
        "Policy($name, ${stages.joinToString(separator = " -> ") { stage -> "${stage.kind.id} ${stage.name}" }})"

    private suspend fun <T> measured(block: suspend () -> T): T {
        val start = timeSource.markNow()
        var isSettled = false
        try {
            val result = attempted { run(index = 0, call = PolicyCall(block)) }
            isSettled = true
            settle(result.exceptionOrNull(), start.elapsedNow())
            return result.getOrThrow()
        } finally {
            if (!isSettled) cancelled()
        }
    }

    private suspend fun <T> run(index: Int, call: PolicyCall<T>): T {
        val layer = layers.getOrNull(index) ?: return call.run()
        return layer.around(call) { run(index = index + 1, call = call) }
    }

    private fun settle(failure: Throwable?, duration: Duration) {
        val rejection = failure?.rejectionOrNull()
        val outcome = outcomeOf(isFailed = failure != null, isRejected = rejection != null)
        guard.locked { counts.count(outcome) }
        meters?.call(outcome, duration)
        sink.emit { eventOf(failure, duration) }
    }

    private fun outcomeOf(isFailed: Boolean, isRejected: Boolean): PolicyOutcome {
        if (!isFailed) return PolicyOutcome.Successful
        return PolicyOutcome.Rejected.takeIf { isRejected } ?: PolicyOutcome.Failed
    }

    private fun eventOf(failure: Throwable?, duration: Duration): PolicyEvent {
        if (failure == null) return PolicyEvent.Succeeded(name, duration)
        val rejection = failure.rejectionOrNull()
        return rejection?.let { refusal ->
            PolicyEvent.Rejected(
                instance = name,
                duration = duration,
                refusedBy = refusal.kind,
                refusingInstance = refusal.instance,
            )
        } ?: PolicyEvent.Failed(name, duration, failure::class.simpleName ?: UNNAMED)
    }

    private fun cancelled() {
        guard.locked { counts.count(PolicyOutcome.Cancelled) }
        meters?.call(PolicyOutcome.Cancelled, duration = null)
        sink.emit { PolicyEvent.Cancelled(name) }
    }
}
