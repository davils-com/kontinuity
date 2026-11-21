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

package com.davils.kontinuity.circuitbreaker

import com.davils.konfetti.core.TopicKey
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceEvent
import com.davils.kontinuity.core.ResilienceKind
import kotlin.time.Duration

/**
 * An event of a [CircuitBreaker]: the outcome of a call, a refusal or a change of its state.
 *
 * @since 1.0.0
 */
public sealed interface CircuitBreakerEvent : ResilienceEvent {
    override val kind: ResilienceKind get() = ResilienceKind.CircuitBreaker

    /**
     * A call succeeded, or failed with a failure the filter passes, which counts as a success.
     *
     * @property instance the name of the breaker.
     * @property duration how long the call took.
     * @property isSlow whether the call was slow.
     * @since 1.0.0
     */
    public class Succeeded internal constructor(
        override val instance: InstanceName,
        public val duration: Duration,
        public val isSlow: Boolean,
    ) : CircuitBreakerEvent {
        /**
         * Shows the breaker and the duration.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Succeeded($instance, $duration, slow=$isSlow)"
    }

    /**
     * A call failed with a recorded failure or a result the filter counts as a failure.
     *
     * @property instance the name of the breaker.
     * @property duration how long the call took.
     * @property isSlow whether the call was slow.
     * @property failureType the simple name of the type of the failure, never its message, or `result` for a result.
     * @since 1.0.0
     */
    public class Failed internal constructor(
        override val instance: InstanceName,
        public val duration: Duration,
        public val isSlow: Boolean,
        public val failureType: String,
    ) : CircuitBreakerEvent {
        /**
         * Shows the breaker, the duration and the type of the failure.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Failed($instance, $duration, slow=$isSlow, $failureType)"
    }

    /**
     * A call failed with an ignored failure, which counts as nothing.
     *
     * @property instance the name of the breaker.
     * @property duration how long the call took.
     * @since 1.0.0
     */
    public class Ignored internal constructor(
        override val instance: InstanceName,
        public val duration: Duration,
    ) : CircuitBreakerEvent {
        /**
         * Shows the breaker and the duration.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Ignored($instance, $duration)"
    }

    /**
     * A permitted call ended without an outcome, such as by the cancellation of its caller; it counts as nothing.
     *
     * @property instance the name of the breaker.
     * @since 1.0.0
     */
    public class Released internal constructor(override val instance: InstanceName) : CircuitBreakerEvent {
        /**
         * Shows the breaker.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Released($instance)"
    }

    /**
     * The breaker refused a call.
     *
     * @property instance the name of the breaker.
     * @property state the state that refused the call.
     * @since 1.0.0
     */
    public class NotPermitted internal constructor(
        override val instance: InstanceName,
        public val state: CircuitState,
    ) : CircuitBreakerEvent {
        /**
         * Shows the breaker and its state.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "NotPermitted($instance, $state)"
    }

    /**
     * The breaker moved from [from] to [to].
     *
     * @property instance the name of the breaker.
     * @property from the previous state.
     * @property to the new state.
     * @since 1.0.0
     */
    public class StateChanged internal constructor(
        override val instance: InstanceName,
        public val from: CircuitState,
        public val to: CircuitState,
    ) : CircuitBreakerEvent {
        /**
         * Shows the breaker and both states.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "StateChanged($instance, $from -> $to)"
    }

    /**
     * The breaker was reset to a closed state with an empty window.
     *
     * @property instance the name of the breaker.
     * @since 1.0.0
     */
    public class Reset internal constructor(override val instance: InstanceName) : CircuitBreakerEvent {
        /**
         * Shows the breaker.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Reset($instance)"
    }

    /**
     * The failure rate reached its threshold.
     *
     * @property instance the name of the breaker.
     * @property rate the failure rate in percent.
     * @since 1.0.0
     */
    public class FailureRateExceeded internal constructor(
        override val instance: InstanceName,
        public val rate: Double,
    ) : CircuitBreakerEvent {
        /**
         * Shows the breaker and the rate.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "FailureRateExceeded($instance, $rate%)"
    }

    /**
     * The rate of slow calls reached its threshold.
     *
     * @property instance the name of the breaker.
     * @property rate the rate of slow calls in percent.
     * @since 1.0.0
     */
    public class SlowCallRateExceeded internal constructor(
        override val instance: InstanceName,
        public val rate: Double,
    ) : CircuitBreakerEvent {
        /**
         * Shows the breaker and the rate.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "SlowCallRateExceeded($instance, $rate%)"
    }

    /**
     * The topic of the events of every circuit breaker.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The topic `kontinuity.circuit-breaker`, declared on the event bus of the environment by the first breaker.
         *
         * @since 1.0.0
         */
        public val TOPIC: TopicKey<CircuitBreakerEvent> = TopicKey("kontinuity.circuit-breaker")
    }
}
