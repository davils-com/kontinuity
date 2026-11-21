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

private const val SUBJECT: String = "dispatcher bulkhead configuration"

/**
 * How a [DispatcherBulkhead] limits its calls, with the defaults of the thread pool bulkhead of resilience4j where
 * they apply.
 *
 * A configuration is an immutable value, validated when it is built and safe to share between bulkheads and threads.
 * Both values have a hard ceiling that no configuration exceeds.
 *
 * @since 1.0.0
 */
public class DispatcherBulkheadConfig internal constructor(builder: DispatcherBulkheadConfigBuilder) : Validatable {
    /**
     * How many calls run on the dispatcher in parallel at most: 8 by default, from 1 to [HARD_CONCURRENT_CALLS]. It is
     * the parallelism the bulkhead asks of its dispatcher, the counterpart of the size of a thread pool.
     *
     * @since 1.0.0
     */
    public val maxConcurrentCalls: Int = builder.maxConcurrentCalls

    /**
     * How many admitted calls may wait for their turn on the dispatcher: 100 by default, as in resilience4j, from 0 to
     * [HARD_QUEUE_CAPACITY].
     *
     * @since 1.0.0
     */
    public val queueCapacity: Int = builder.queueCapacity

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
        inRange(property = ::queueCapacity, value = queueCapacity, range = 0..HARD_QUEUE_CAPACITY)
    }

    /**
     * Shows every value.
     *
     * @return the configuration as text.
     * @since 1.0.0
     */
    override fun toString(): String =
        "DispatcherBulkheadConfig(maxConcurrentCalls=$maxConcurrentCalls, queueCapacity=$queueCapacity)"

    /**
     * The hard ceilings and the default configuration.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The most calls that run in parallel: 10,000.
         *
         * @since 1.0.0
         */
        public const val HARD_CONCURRENT_CALLS: Int = 10_000

        /**
         * The most admitted calls that wait for their turn: 100,000.
         *
         * @since 1.0.0
         */
        public const val HARD_QUEUE_CAPACITY: Int = 100_000

        internal const val DEFAULT_CONCURRENT_CALLS: Int = 8
        internal const val DEFAULT_QUEUE_CAPACITY: Int = 100

        /**
         * The configuration with 8 calls in parallel and room for 100 waiting calls.
         *
         * @since 1.0.0
         */
        public val Default: DispatcherBulkheadConfig = DispatcherBulkheadConfig(DispatcherBulkheadConfigBuilder())
    }
}
