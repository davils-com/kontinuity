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

import com.davils.kontinuity.core.filter.FailureFilter
import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.validateOrThrow

private const val SUBJECT: String = "hedge configuration"

/**
 * How a [Hedge] starts the copies of a slow call, with the defaults of resilience4j: one extra copy after 100
 * milliseconds.
 *
 * A configuration is an immutable value, validated when it is built and safe to share between hedges and threads.
 * A registry hands the same configuration to many hedges.
 *
 * @since 1.0.0
 */
public class HedgeConfig internal constructor(builder: HedgeConfigBuilder) : Validatable {
    /**
     * How long the hedge waits for the latest copy before it starts the next one: a fixed delay of 100 milliseconds
     * by default. Every delay is positive and at most [HedgeDelay.HARD_DELAY].
     *
     * @since 1.0.0
     */
    public val delay: HedgeDelay = builder.delay

    /**
     * How many copies the hedge starts in addition to the primary: 1 by default, from 1 to [HARD_HEDGED_ATTEMPTS].
     * A call therefore runs the protected block at most `maxHedgedAttempts + 1` times.
     *
     * @since 1.0.0
     */
    public val maxHedgedAttempts: Int = builder.maxHedgedAttempts

    /**
     * Which failures of a copy are worth another copy: every failure but the rejections of kontinuity by default.
     * A recorded failure starts the next copy at once, if one is left; a failure the filter ignores or passes ends
     * the whole call at once with that failure, and so does every rejection.
     *
     * @since 1.0.0
     */
    public val failures: FailureFilter = builder.failures

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
        inRange(property = ::maxHedgedAttempts, value = maxHedgedAttempts, range = 1..HARD_HEDGED_ATTEMPTS)
    }

    /**
     * Shows the delay, the number of copies and the failure filter.
     *
     * @return the configuration as text.
     * @since 1.0.0
     */
    override fun toString(): String =
        "HedgeConfig(delay=$delay, maxHedgedAttempts=$maxHedgedAttempts, failures=$failures)"

    /**
     * The hard ceiling and the default configuration.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The most copies a hedge starts in addition to the primary: 10.
         *
         * @since 1.0.0
         */
        public const val HARD_HEDGED_ATTEMPTS: Int = 10

        internal const val DEFAULT_HEDGED_ATTEMPTS: Int = 1

        /**
         * The configuration with the defaults: one extra copy after a fixed delay of 100 milliseconds.
         *
         * @since 1.0.0
         */
        public val Default: HedgeConfig = HedgeConfig(HedgeConfigBuilder())
    }
}
