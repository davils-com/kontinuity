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

import com.davils.konfetti.core.TopicKey
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceEvent
import com.davils.kontinuity.core.ResilienceKind
import kotlin.time.Duration

/**
 * An event of a [Policy]: how a call through every stage of the policy ended.
 *
 * @since 1.0.0
 */
public sealed interface PolicyEvent : ResilienceEvent {
    override val kind: ResilienceKind get() = ResilienceKind.Policy

    /**
     * The call returned a result.
     *
     * @property instance the name of the policy.
     * @property duration how long the call took, every wait and retry included.
     * @since 1.0.0
     */
    public class Succeeded internal constructor(
        override val instance: InstanceName,
        public val duration: Duration,
    ) : PolicyEvent {
        /**
         * Shows the policy and the duration.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Succeeded($instance, $duration)"
    }

    /**
     * The call failed.
     *
     * @property instance the name of the policy.
     * @property duration how long the call took.
     * @property failureType the simple name of the type of the failure, never its message.
     * @since 1.0.0
     */
    public class Failed internal constructor(
        override val instance: InstanceName,
        public val duration: Duration,
        public val failureType: String,
    ) : PolicyEvent {
        /**
         * Shows the policy, the duration and the type of the failure.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Failed($instance, $duration, $failureType)"
    }

    /**
     * A stage of the policy refused the call.
     *
     * @property instance the name of the policy.
     * @property duration how long the call took until the refusal.
     * @property refusedBy the kind of the stage that refused the call.
     * @property refusingInstance the name of the stage that refused the call.
     * @since 1.0.0
     */
    public class Rejected internal constructor(
        override val instance: InstanceName,
        public val duration: Duration,
        public val refusedBy: ResilienceKind,
        public val refusingInstance: InstanceName,
    ) : PolicyEvent {
        /**
         * Shows the policy and the refusing stage.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Rejected($instance, ${refusedBy.id} $refusingInstance)"
    }

    /**
     * The caller was cancelled before the call ended.
     *
     * @property instance the name of the policy.
     * @since 1.0.0
     */
    public class Cancelled internal constructor(override val instance: InstanceName) : PolicyEvent {
        /**
         * Shows the policy.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Cancelled($instance)"
    }

    /**
     * The topic of the events of every policy.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The topic `kontinuity.policy`, declared on the event bus of the environment by the first policy.
         *
         * @since 1.0.0
         */
        public val TOPIC: TopicKey<PolicyEvent> = TopicKey("kontinuity.policy")
    }
}
