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

import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.validateOrThrow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

private const val SUBJECT: String = "bulkhead configuration"

/**
 * How a [Bulkhead] limits the calls that run at once, with the defaults of the semaphore bulkhead of resilience4j.
 *
 * A configuration is an immutable value, validated when it is built and safe to share between bulkheads and threads.
 * Every value has a hard ceiling that no configuration exceeds, so that neither an attacker nor a broken configuration
 * can make a bulkhead hold an unbounded queue or let a caller wait without end. A running bulkhead takes a new
 * configuration with [Bulkhead.changeConfig].
 *
 * @since 1.0.0
 */
public class BulkheadConfig internal constructor(builder: BulkheadConfigBuilder) : Validatable {
    /**
     * How many calls may hold a permit at once: 25 by default, from 1 to [HARD_CONCURRENT_CALLS].
     *
     * @since 1.0.0
     */
    public val maxConcurrentCalls: Int = builder.maxConcurrentCalls

    /**
     * How long a call waits for a permit before the bulkhead refuses it: zero by default, as in resilience4j, so that
     * a full bulkhead refuses at once; from zero to [HARD_WAIT].
     *
     * @since 1.0.0
     */
    public val maxWaitDuration: Duration = builder.maxWaitDuration

    /**
     * How many calls may wait for a permit at once: 1,000 by default, from 0 to [HARD_WAITING_CALLS]. A call that
     * finds the queue full is refused at once, whatever its wait.
     *
     * @since 1.0.0
     */
    public val maxWaitingCalls: Int = builder.maxWaitingCalls

    init {
        validateOrThrow(SUBJECT)
    }

    /**
     * Checks every value against its bounds.
     *
     * @receiver the validation that collects the violations.
     * @since 1.0.0
     */
    override fun ValidationScope.validate() {
        inRange(property = ::maxConcurrentCalls, value = maxConcurrentCalls, range = 1..HARD_CONCURRENT_CALLS)
        inRange(property = ::maxWaitDuration, value = maxWaitDuration, range = Duration.ZERO..HARD_WAIT)
        inRange(property = ::maxWaitingCalls, value = maxWaitingCalls, range = 0..HARD_WAITING_CALLS)
    }

    /**
     * Shows every value.
     *
     * @return the configuration as text.
     * @since 1.0.0
     */
    override fun toString(): String = "BulkheadConfig(maxConcurrentCalls=$maxConcurrentCalls, " +
        "maxWaitDuration=$maxWaitDuration, maxWaitingCalls=$maxWaitingCalls)"

    /**
     * The hard ceilings and the default configuration.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The most calls that may hold a permit at once: 100,000.
         *
         * @since 1.0.0
         */
        public const val HARD_CONCURRENT_CALLS: Int = 100_000

        /**
         * The most calls that may wait for a permit at once: 100,000.
         *
         * @since 1.0.0
         */
        public const val HARD_WAITING_CALLS: Int = 100_000

        /**
         * The longest wait for a permit: one hour. It bounds [maxWaitDuration] and every timeout of
         * [Bulkhead.acquirePermission].
         *
         * @since 1.0.0
         */
        public val HARD_WAIT: Duration = 1.hours

        internal const val DEFAULT_CONCURRENT_CALLS: Int = 25
        internal const val DEFAULT_WAITING_CALLS: Int = 1_000

        /**
         * The configuration with the defaults of resilience4j: 25 concurrent calls, no wait and room for 1,000
         * waiting calls once a wait is configured.
         *
         * @since 1.0.0
         */
        public val Default: BulkheadConfig = BulkheadConfig(BulkheadConfigBuilder())
    }
}
