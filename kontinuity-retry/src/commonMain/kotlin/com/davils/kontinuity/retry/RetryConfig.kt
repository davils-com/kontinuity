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

import com.davils.kontinuity.core.backoff.Backoff
import com.davils.kontinuity.core.backoff.fixed
import com.davils.kontinuity.core.filter.FailureFilter
import com.davils.kontinuity.core.filter.ResultFilter
import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.atMost
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.validateOrThrow
import kotlin.time.Duration.Companion.milliseconds

private const val SUBJECT: String = "retry configuration"

/**
 * How a [Retry] repeats a call, with the defaults of resilience4j.
 *
 * A configuration is an immutable value, validated when it is built and safe to share between retries and threads;
 * the foreign code it holds, such as [interval] and [onDiscardedResult], must be safe to call from many threads at
 * once. A registry hands the same configuration to many retries.
 *
 * @since 1.0.0
 */
public class RetryConfig internal constructor(builder: RetryConfigBuilder) : Validatable {
    /**
     * How many attempts a call may take, the first included: 3 by default, from 1 to [HARD_ATTEMPTS]; 1 never
     * retries.
     *
     * @since 1.0.0
     */
    public val maxAttempts: Int = builder.maxAttempts

    /**
     * The wait before every retry: 500 milliseconds, fixed, by default. The attempt of the backoff is the number of
     * the attempt that follows the wait, so an exponential backoff waits its initial delay before the first retry.
     * Its jitter draws from the entropy of the environment. Its [Backoff.maximum] is at most
     * [Backoff.HARD_MAXIMUM_DELAY].
     *
     * @since 1.0.0
     */
    public val backoff: Backoff = builder.backoff

    /**
     * Which failures the retry repeats: every failure but the rejections of kontinuity by default. A failure that the
     * filter records is repeated; one it passes or ignores ends the call at once and is rethrown.
     *
     * @since 1.0.0
     */
    public val failures: FailureFilter = builder.failures

    /**
     * Which results the retry repeats: none by default. A rule of the filter that throws ends the call with what it
     * threw.
     *
     * @since 1.0.0
     */
    public val results: ResultFilter = builder.results

    /**
     * Chooses the wait before a retry by the failure or the result before it, instead of [backoff]: none by default.
     *
     * @since 1.0.0
     */
    public val interval: RetryInterval? = builder.interval

    /**
     * Whether a call whose results keep failing ends with a [RetriesExhaustedException] when its attempts run out:
     * false by default, so that it returns its last result as resilience4j does.
     *
     * @since 1.0.0
     */
    public val isFailingAfterMaxAttempts: Boolean = builder.isFailingAfterMaxAttempts

    /**
     * Receives every result the retry does not hand back to its caller, such as a response to close: the results
     * it repeats, the last result when it throws a [RetriesExhaustedException] and a result whose filter threw. None
     * by default. It is foreign code: what it throws is dropped and a fatal error passes through.
     *
     * @since 1.0.0
     */
    public val onDiscardedResult: ((Any?) -> Unit)? = builder.onDiscardedResult

    /**
     * The budget that bounds the retries of this and other retries: none by default.
     *
     * @since 1.0.0
     */
    public val budget: RetryBudget? = builder.budget

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
        inRange(property = ::maxAttempts, value = maxAttempts, range = 1..HARD_ATTEMPTS)
        atMost(property = ::backoff, value = backoff.maximum, maximum = Backoff.HARD_MAXIMUM_DELAY)
    }

    /**
     * Shows the attempts, the backoff and which hooks are set.
     *
     * @return the configuration as text.
     * @since 1.0.0
     */
    override fun toString(): String = "RetryConfig(maxAttempts=$maxAttempts, backoff=$backoff, " +
        "failingAfterMaxAttempts=$isFailingAfterMaxAttempts, interval=${interval != null}, budget=${budget != null})"

    /**
     * The hard ceiling and the default configuration.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The most attempts of a call: 100.
         *
         * @since 1.0.0
         */
        public const val HARD_ATTEMPTS: Int = 100

        internal const val DEFAULT_ATTEMPTS: Int = 3
        internal val DEFAULT_BACKOFF: Backoff = fixed(500.milliseconds)

        /**
         * The configuration with the defaults of resilience4j.
         *
         * @since 1.0.0
         */
        public val Default: RetryConfig = RetryConfig(RetryConfigBuilder())
    }
}
