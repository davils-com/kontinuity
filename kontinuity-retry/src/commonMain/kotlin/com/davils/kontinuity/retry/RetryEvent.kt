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

package com.davils.kontinuity.retry

import com.davils.konfetti.core.TopicKey
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceEvent
import com.davils.kontinuity.core.ResilienceKind
import kotlin.time.Duration

/**
 * An event of a [Retry]: a planned retry, the end of a call or a retry the budget denied.
 *
 * Events name the types of failures and results by their simple names and never carry their messages or values, so
 * that no data of a call reaches a listener.
 *
 * @since 1.0.0
 */
public sealed interface RetryEvent : ResilienceEvent {
    override val kind: ResilienceKind get() = ResilienceKind.Retry

    /**
     * An attempt failed or returned a failing result, and the retry waits [delay] before the next attempt.
     *
     * @property instance the name of the retry.
     * @property attempt the number of the attempt that follows the wait: 2 before the first retry.
     * @property delay the wait before that attempt.
     * @property reasonType the simple name of the type of the failure, never its message, or `result` for a result.
     * @since 1.0.0
     */
    public class Retrying internal constructor(
        override val instance: InstanceName,
        public val attempt: Int,
        public val delay: Duration,
        public val reasonType: String,
    ) : RetryEvent {
        /**
         * Shows the retry, the attempt, the wait and the reason.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Retrying($instance, attempt=$attempt, $delay, $reasonType)"
    }

    /**
     * A call succeeded.
     *
     * @property instance the name of the retry.
     * @property attempts how many attempts the call took.
     * @since 1.0.0
     */
    public class Succeeded internal constructor(
        override val instance: InstanceName,
        public val attempts: Int,
    ) : RetryEvent {
        /**
         * Shows the retry and the attempts.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Succeeded($instance, attempts=$attempts)"
    }

    /**
     * A call failed with a failure or a result the retry would repeat, but it ran out of attempts or its budget
     * denied the next one.
     *
     * @property instance the name of the retry.
     * @property attempts how many attempts the call took.
     * @property failureType the simple name of the type of the last failure, never its message, or `result` for a
     *   result.
     * @since 1.0.0
     */
    public class Exhausted internal constructor(
        override val instance: InstanceName,
        public val attempts: Int,
        public val failureType: String,
    ) : RetryEvent {
        /**
         * Shows the retry, the attempts and the type of the failure.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Exhausted($instance, attempts=$attempts, $failureType)"
    }

    /**
     * A call failed with a failure the retry does not repeat: one its failure filter passes or ignores, such as a
     * rejection of another component, or one its result filter threw.
     *
     * @property instance the name of the retry.
     * @property failureType the simple name of the type of the failure, never its message.
     * @since 1.0.0
     */
    public class IgnoredFailure internal constructor(
        override val instance: InstanceName,
        public val failureType: String,
    ) : RetryEvent {
        /**
         * Shows the retry and the type of the failure.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "IgnoredFailure($instance, $failureType)"
    }

    /**
     * The retry budget denied an attempt, so the call ends with its last failure or its last result.
     *
     * @property instance the name of the retry.
     * @property attempt the number of the attempt the budget denied.
     * @since 1.0.0
     */
    public class BudgetDenied internal constructor(
        override val instance: InstanceName,
        public val attempt: Int,
    ) : RetryEvent {
        /**
         * Shows the retry and the denied attempt.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "BudgetDenied($instance, attempt=$attempt)"
    }

    /**
     * The topic of the events of every retry.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The topic `kontinuity.retry`, declared on the event bus of the environment by the first retry.
         *
         * @since 1.0.0
         */
        public val TOPIC: TopicKey<RetryEvent> = TopicKey("kontinuity.retry")
    }
}
