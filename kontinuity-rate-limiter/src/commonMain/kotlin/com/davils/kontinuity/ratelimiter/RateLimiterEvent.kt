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

package com.davils.kontinuity.ratelimiter

import com.davils.konfetti.core.TopicKey
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceEvent
import com.davils.kontinuity.core.ResilienceKind
import kotlin.time.Duration

/**
 * An event of a [RateLimiter]: the outcome of a request for permits, a drain or a change of its limits.
 *
 * Every request for permits ends in exactly one of [Acquired], [Rejected] and [Cancelled].
 *
 * @since 1.0.0
 */
public sealed interface RateLimiterEvent : ResilienceEvent {
    override val kind: ResilienceKind get() = ResilienceKind.RateLimiter

    /**
     * A caller got its permits.
     *
     * @property instance the name of the limiter.
     * @property permits how many permits it got.
     * @property waited how long the limiter made it wait for them; zero if they were free at once.
     * @since 1.0.0
     */
    public class Acquired internal constructor(
        override val instance: InstanceName,
        public val permits: Int,
        public val waited: Duration,
    ) : RateLimiterEvent {
        /**
         * Shows the limiter, the permits and the wait.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Acquired($instance, permits=$permits, waited=$waited)"
    }

    /**
     * The limiter refused a caller.
     *
     * @property instance the name of the limiter.
     * @property permits how many permits the caller asked for.
     * @property availableIn how long the caller would have had to wait for them.
     * @since 1.0.0
     */
    public class Rejected internal constructor(
        override val instance: InstanceName,
        public val permits: Int,
        public val availableIn: Duration,
    ) : RateLimiterEvent {
        /**
         * Shows the limiter, the permits and when they would be free.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Rejected($instance, permits=$permits, availableIn=$availableIn)"
    }

    /**
     * A caller was cancelled or interrupted while it waited for its permits.
     *
     * @property instance the name of the limiter.
     * @property permits how many permits the caller had reserved.
     * @property isRefunded whether the limiter took the reservation back for later callers.
     * @since 1.0.0
     */
    public class Cancelled internal constructor(
        override val instance: InstanceName,
        public val permits: Int,
        public val isRefunded: Boolean,
    ) : RateLimiterEvent {
        /**
         * Shows the limiter, the permits and whether they were given back.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Cancelled($instance, permits=$permits, refunded=$isRefunded)"
    }

    /**
     * The remaining permits of the current period were drained, by hand or by the outcome of a call.
     *
     * @property instance the name of the limiter.
     * @property permits how many free permits were drained.
     * @since 1.0.0
     */
    public class Drained internal constructor(
        override val instance: InstanceName,
        public val permits: Int,
    ) : RateLimiterEvent {
        /**
         * Shows the limiter and the drained permits.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Drained($instance, permits=$permits)"
    }

    /**
     * The limit per period or the timeout changed at runtime.
     *
     * @property instance the name of the limiter.
     * @property limitForPeriod the limit per period from now on.
     * @property timeout the timeout of callers from now on.
     * @since 1.0.0
     */
    public class LimitChanged internal constructor(
        override val instance: InstanceName,
        public val limitForPeriod: Int,
        public val timeout: Duration,
    ) : RateLimiterEvent {
        /**
         * Shows the limiter and its new limits.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "LimitChanged($instance, limitForPeriod=$limitForPeriod, timeout=$timeout)"
    }

    /**
     * The topic of the events of every rate limiter.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The topic `kontinuity.rate-limiter`, declared on the event bus of the environment by the first limiter.
         *
         * @since 1.0.0
         */
        public val TOPIC: TopicKey<RateLimiterEvent> = TopicKey("kontinuity.rate-limiter")
    }
}
