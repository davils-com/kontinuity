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

import com.davils.konfetti.core.TopicKey
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceEvent
import com.davils.kontinuity.core.ResilienceKind
import kotlin.time.Duration

/**
 * An event of a [Bulkhead]: a permit handed out, a call refused or a permit given back.
 *
 * Events show only the name of the bulkhead, durations and flags, never anything of the protected call.
 *
 * @since 1.0.0
 */
public sealed interface BulkheadEvent : ResilienceEvent {
    override val kind: ResilienceKind get() = ResilienceKind.Bulkhead

    /**
     * The bulkhead handed a permit to a call.
     *
     * @property instance the name of the bulkhead.
     * @property waited how long the call waited for the permit; zero when a permit was free.
     * @since 1.0.0
     */
    public class Permitted internal constructor(
        override val instance: InstanceName,
        public val waited: Duration,
    ) : BulkheadEvent {
        /**
         * Shows the bulkhead and the wait.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Permitted($instance, waited=$waited)"
    }

    /**
     * The bulkhead refused a call.
     *
     * @property instance the name of the bulkhead.
     * @property isQueueFull true if the queue of waiting calls was full, false if the call was not allowed to wait or
     *   its wait ran out.
     * @since 1.0.0
     */
    public class Rejected internal constructor(
        override val instance: InstanceName,
        public val isQueueFull: Boolean,
    ) : BulkheadEvent {
        /**
         * Shows the bulkhead and why it refused the call.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Rejected($instance, queueFull=$isQueueFull)"
    }

    /**
     * A call gave its permit back.
     *
     * @property instance the name of the bulkhead.
     * @property held how long the call held the permit.
     * @since 1.0.0
     */
    public class Finished internal constructor(
        override val instance: InstanceName,
        public val held: Duration,
    ) : BulkheadEvent {
        /**
         * Shows the bulkhead and how long the permit was held.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Finished($instance, held=$held)"
    }

    /**
     * The topic of the events of every bulkhead.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The topic `kontinuity.bulkhead`, declared on the event bus of the environment by the first bulkhead.
         *
         * @since 1.0.0
         */
        public val TOPIC: TopicKey<BulkheadEvent> = TopicKey("kontinuity.bulkhead")
    }
}
