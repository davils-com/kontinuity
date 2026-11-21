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

package com.davils.kontinuity.hedge

import com.davils.konfetti.core.TopicKey
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceEvent
import com.davils.kontinuity.core.ResilienceKind
import kotlin.time.Duration

/**
 * An event of a [Hedge]: the start of a copy or the outcome of a call.
 *
 * Every call ends in exactly one of [PrimarySucceeded], [HedgeSucceeded], [Failed] and [Cancelled]. The events
 * never carry the result or the message of a failure, only the simple name of its type.
 *
 * @since 1.0.0
 */
public sealed interface HedgeEvent : ResilienceEvent {
    override val kind: ResilienceKind get() = ResilienceKind.Hedge

    /**
     * The primary copy of a call succeeded first; every hedged copy that was started is cancelled.
     *
     * @property instance the name of the hedge.
     * @property duration how long the call took.
     * @since 1.0.0
     */
    public class PrimarySucceeded internal constructor(
        override val instance: InstanceName,
        public val duration: Duration,
    ) : HedgeEvent {
        /**
         * Shows the hedge and the duration.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "PrimarySucceeded($instance, $duration)"
    }

    /**
     * The hedge started another copy of a call, because the latest copy was slower than the delay or failed.
     *
     * @property instance the name of the hedge.
     * @property attempt the number of the copy, from 1 for the first hedged copy to
     *   [HedgeConfig.maxHedgedAttempts]; the primary is copy 0.
     * @property after how long after the start of the call the copy started.
     * @since 1.0.0
     */
    public class HedgeStarted internal constructor(
        override val instance: InstanceName,
        public val attempt: Int,
        public val after: Duration,
    ) : HedgeEvent {
        /**
         * Shows the hedge, the copy and when it started.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "HedgeStarted($instance, attempt=$attempt, after=$after)"
    }

    /**
     * A hedged copy of a call succeeded first; every other copy is cancelled.
     *
     * @property instance the name of the hedge.
     * @property attempt the number of the copy that won, from 1 to [HedgeConfig.maxHedgedAttempts].
     * @property duration how long the call took, from the start of the primary.
     * @since 1.0.0
     */
    public class HedgeSucceeded internal constructor(
        override val instance: InstanceName,
        public val attempt: Int,
        public val duration: Duration,
    ) : HedgeEvent {
        /**
         * Shows the hedge, the copy and the duration.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "HedgeSucceeded($instance, attempt=$attempt, $duration)"
    }

    /**
     * A call failed: every copy failed, or one copy failed with a failure that the filter does not record, such as
     * a rejection.
     *
     * @property instance the name of the hedge.
     * @property attempts how many copies the call started, the primary included.
     * @property failureType the simple name of the type of the failure the call threw, never its message.
     * @since 1.0.0
     */
    public class Failed internal constructor(
        override val instance: InstanceName,
        public val attempts: Int,
        public val failureType: String,
    ) : HedgeEvent {
        /**
         * Shows the hedge, the number of copies and the type of the failure.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Failed($instance, attempts=$attempts, $failureType)"
    }

    /**
     * The caller of a call was cancelled; every copy was cancelled with it.
     *
     * @property instance the name of the hedge.
     * @since 1.0.0
     */
    public class Cancelled internal constructor(override val instance: InstanceName) : HedgeEvent {
        /**
         * Shows the hedge.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Cancelled($instance)"
    }

    /**
     * The topic of the events of every hedge.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The topic `kontinuity.hedge`, declared on the event bus of the environment by the first hedge.
         *
         * @since 1.0.0
         */
        public val TOPIC: TopicKey<HedgeEvent> = TopicKey("kontinuity.hedge")
    }
}
