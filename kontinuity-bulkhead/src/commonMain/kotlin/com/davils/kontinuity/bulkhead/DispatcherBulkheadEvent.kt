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
 * An event of a [DispatcherBulkhead]: a call started on the dispatcher, a call refused or a call ended.
 *
 * Events show only the name of the bulkhead, durations and flags, never anything of the protected call.
 *
 * @since 1.0.0
 */
public sealed interface DispatcherBulkheadEvent : ResilienceEvent {
    override val kind: ResilienceKind get() = ResilienceKind.DispatcherBulkhead

    /**
     * An admitted call started on the dispatcher.
     *
     * @property instance the name of the bulkhead.
     * @property waited how long the call waited in the queue for its turn on the dispatcher.
     * @since 1.0.0
     */
    public class Permitted internal constructor(
        override val instance: InstanceName,
        public val waited: Duration,
    ) : DispatcherBulkheadEvent {
        /**
         * Shows the bulkhead and the wait.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Permitted($instance, waited=$waited)"
    }

    /**
     * The bulkhead refused a call because its running and queued calls filled its capacity.
     *
     * @property instance the name of the bulkhead.
     * @property isQueueFull always true: a dispatcher bulkhead refuses a call only when its queue is full.
     * @since 1.0.0
     */
    public class Rejected internal constructor(
        override val instance: InstanceName,
        public val isQueueFull: Boolean,
    ) : DispatcherBulkheadEvent {
        /**
         * Shows the bulkhead and why it refused the call.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Rejected($instance, queueFull=$isQueueFull)"
    }

    /**
     * A call that ran on the dispatcher ended, however it ended.
     *
     * @property instance the name of the bulkhead.
     * @property held how long the call ran.
     * @since 1.0.0
     */
    public class Finished internal constructor(
        override val instance: InstanceName,
        public val held: Duration,
    ) : DispatcherBulkheadEvent {
        /**
         * Shows the bulkhead and how long the call ran.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Finished($instance, held=$held)"
    }

    /**
     * The topic of the events of every dispatcher bulkhead.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The topic `kontinuity.dispatcher-bulkhead`, declared on the event bus of the environment by the first
         * dispatcher bulkhead.
         *
         * @since 1.0.0
         */
        public val TOPIC: TopicKey<DispatcherBulkheadEvent> = TopicKey("kontinuity.dispatcher-bulkhead")
    }
}
