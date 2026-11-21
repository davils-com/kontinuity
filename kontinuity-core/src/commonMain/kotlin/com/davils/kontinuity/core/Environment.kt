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

package com.davils.kontinuity.core

import com.davils.kollect.core.MeterRegistry
import com.davils.kollect.core.trace.Tracer
import com.davils.konfetti.core.EventBus
import com.davils.konfetti.core.bus.Entropy
import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.validateOrThrow
import kotlinx.coroutines.CoroutineScope
import kotlin.time.TimeSource

private const val SUBJECT: String = "environment"

/**
 * What the instances of kontinuity take from their surroundings: the time, the randomness, the event bus, the meter
 * registry, the tracer and the scope of background work.
 *
 * An environment holds no state of its own, so one environment can serve every instance of an application; a
 * registry hands its environment to every instance it creates. Without an event bus the events of an instance are
 * only a `Flow`; without a meter registry it records no meters.
 *
 * @since 1.0.0
 */
public class Environment internal constructor(builder: EnvironmentBuilder) : Validatable {
    /**
     * The monotonic time source every wait, window and duration is measured with; [TimeSource.Monotonic] by default.
     * Tests hand in the time source of their test scheduler.
     *
     * @since 1.0.0
     */
    public val timeSource: TimeSource.WithComparableMarks = builder.timeSource

    /**
     * The randomness of jitter and hedging; secure randomness by default. A failing entropy is contained: the call
     * then uses secure randomness.
     *
     * @since 1.0.0
     */
    public val entropy: Entropy = builder.entropy

    /**
     * The event bus whose topics carry the events of every instance, or null for none.
     *
     * @since 1.0.0
     */
    public val events: EventBus? = builder.events

    /**
     * The meter registry every instance records its meters in, or null for none.
     *
     * @since 1.0.0
     */
    public val meters: MeterRegistry? = builder.meters

    /**
     * The tracer of a policy, which opens a span for every call through it; [Tracer.None] by default.
     *
     * @since 1.0.0
     */
    public val tracer: Tracer = builder.tracer

    /**
     * The scope of the caller that runs the background work of the instances, or null for none: the automatic
     * transition of a circuit breaker, the detached calls of a soft time limiter and the reaper of a cache. kontinuity
     * starts no coroutine without it; a configuration that needs one is refused when the instance is created.
     *
     * @since 1.0.0
     */
    public val scope: CoroutineScope? = builder.scope

    /**
     * How many events of one instance wait for a slow listener of its `Flow` before the oldest is dropped: 64 by
     * default, from 1 to [HARD_EVENT_CAPACITY].
     *
     * @since 1.0.0
     */
    public val eventCapacity: Int = builder.eventCapacity

    init {
        validateOrThrow(SUBJECT)
    }

    /**
     * Checks that [eventCapacity] is within its bounds.
     *
     * @receiver the validation that collects the violations.
     * @since 1.0.0
     */
    override fun ValidationScope.validate() {
        inRange(property = ::eventCapacity, value = eventCapacity, range = 1..HARD_EVENT_CAPACITY)
    }

    /**
     * Shows which parts of the environment are set.
     *
     * @return the environment as text.
     * @since 1.0.0
     */
    override fun toString(): String =
        "Environment(events=${events != null}, meters=${meters != null}, scope=${scope != null}, " +
            "eventCapacity=$eventCapacity)"

    /**
     * The hard ceiling and the default environment.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The hard ceiling of [eventCapacity]: 4,096.
         *
         * @since 1.0.0
         */
        public const val HARD_EVENT_CAPACITY: Int = 4_096

        internal const val DEFAULT_EVENT_CAPACITY: Int = 64

        /**
         * The environment with the monotonic time, secure randomness, no event bus, no meter registry, no tracer and
         * no scope.
         *
         * @since 1.0.0
         */
        public val Default: Environment = Environment(EnvironmentBuilder())
    }
}
