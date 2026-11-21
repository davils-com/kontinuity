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

package com.davils.kontinuity.timelimiter

import com.davils.konfetti.core.TopicKey
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceEvent
import com.davils.kontinuity.core.ResilienceKind
import kotlin.time.Duration

/**
 * An event of a [TimeLimiter]: the outcome of a call, a refusal or the failure of a detached call.
 *
 * The cancellation of a caller emits no event; it is counted in the snapshot and the meters. No event carries the
 * message of a failure, which may hold secrets, only the simple name of its type.
 *
 * @since 1.0.0
 */
public sealed interface TimeLimiterEvent : ResilienceEvent {
    override val kind: ResilienceKind get() = ResilienceKind.TimeLimiter

    /**
     * A call returned within its timeout.
     *
     * @property instance the name of the limiter.
     * @property duration how long the call took.
     * @since 1.0.0
     */
    public class Succeeded internal constructor(
        override val instance: InstanceName,
        public val duration: Duration,
    ) : TimeLimiterEvent {
        /**
         * Shows the limiter and the duration.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Succeeded($instance, $duration)"
    }

    /**
     * A call failed within its timeout, such as by the timeout of a callee.
     *
     * @property instance the name of the limiter.
     * @property duration how long the call took.
     * @property failureType the simple name of the type of the failure, never its message.
     * @since 1.0.0
     */
    public class Failed internal constructor(
        override val instance: InstanceName,
        public val duration: Duration,
        public val failureType: String,
    ) : TimeLimiterEvent {
        /**
         * Shows the limiter, the duration and the type of the failure.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Failed($instance, $duration, $failureType)"
    }

    /**
     * A call ran out of time.
     *
     * @property instance the name of the limiter.
     * @property timeout the timeout the call ran out of.
     * @since 1.0.0
     */
    public class TimedOut internal constructor(
        override val instance: InstanceName,
        public val timeout: Duration,
    ) : TimeLimiterEvent {
        /**
         * Shows the limiter and the timeout.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "TimedOut($instance, $timeout)"
    }

    /**
     * A detached call of a soft limiter failed after its caller had stopped waiting for it.
     *
     * @property instance the name of the limiter.
     * @property failureType the simple name of the type of the failure, never its message.
     * @since 1.0.0
     */
    public class DetachedFailed internal constructor(
        override val instance: InstanceName,
        public val failureType: String,
    ) : TimeLimiterEvent {
        /**
         * Shows the limiter and the type of the failure.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "DetachedFailed($instance, $failureType)"
    }

    /**
     * A soft limiter refused a call because its detached calls were at their limit.
     *
     * @property instance the name of the limiter.
     * @property maxDetached the most detached calls of the limiter.
     * @since 1.0.0
     */
    public class DetachedRejected internal constructor(
        override val instance: InstanceName,
        public val maxDetached: Int,
    ) : TimeLimiterEvent {
        /**
         * Shows the limiter and its limit.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "DetachedRejected($instance, maxDetached=$maxDetached)"
    }

    /**
     * The topic of the events of every time limiter.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The topic `kontinuity.time-limiter`, declared on the event bus of the environment by the first limiter.
         *
         * @since 1.0.0
         */
        public val TOPIC: TopicKey<TimeLimiterEvent> = TopicKey("kontinuity.time-limiter")
    }
}
