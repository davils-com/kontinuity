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

package com.davils.kontinuity.core

import com.davils.kollect.core.MeterRegistry
import com.davils.kollect.core.trace.Tracer
import com.davils.konfetti.core.EventBus
import com.davils.konfetti.core.bus.Entropy
import com.davils.kontinuity.core.telemetry.SecureEntropy
import com.davils.kore.core.annotation.DavilsDsl
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.dsl.ValidatingBuilder
import kotlinx.coroutines.CoroutineScope
import kotlin.time.TimeSource

/**
 * Builds an [Environment]; every property starts from the environment [environment] starts from.
 *
 * @since 1.0.0
 */
@DavilsDsl
public class EnvironmentBuilder internal constructor() : ValidatingBuilder<Environment>() {
    override val subject: String get() = "environment"

    /**
     * See [Environment.timeSource].
     *
     * @since 1.0.0
     */
    public var timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [Environment.entropy].
     *
     * @since 1.0.0
     */
    public var entropy: Entropy = SecureEntropy
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [Environment.tracer].
     *
     * @since 1.0.0
     */
    public var tracer: Tracer = Tracer.None
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [Environment.eventCapacity].
     *
     * @since 1.0.0
     */
    public var eventCapacity: Int = Environment.DEFAULT_EVENT_CAPACITY
        set(value) {
            checkOpen()
            field = value
        }

    internal var events: EventBus? = null
        private set

    internal var meters: MeterRegistry? = null
        private set

    internal var scope: CoroutineScope? = null
        private set

    /**
     * Publishes the events of every instance on the topics of [bus], too; see [Environment.events].
     *
     * The topic of a kind of instance is declared on the bus when the first instance of that kind is created.
     *
     * @param bus the event bus.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun events(bus: EventBus) {
        checkOpen()
        events = bus
    }

    /**
     * Records the meters of every instance in [registry]; see [Environment.meters].
     *
     * @param registry the meter registry.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun meters(registry: MeterRegistry) {
        checkOpen()
        meters = registry
    }

    /**
     * Runs the background work of the instances in [scope]; see [Environment.scope].
     *
     * @param scope the scope of the caller, such as the scope of the application.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun scope(scope: CoroutineScope) {
        checkOpen()
        this.scope = scope
    }

    internal fun from(base: Environment) {
        timeSource = base.timeSource
        entropy = base.entropy
        tracer = base.tracer
        eventCapacity = base.eventCapacity
        events = base.events
        meters = base.meters
        scope = base.scope
    }

    override fun assemble(): Environment = Environment(this)
}
