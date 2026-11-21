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

package com.davils.kontinuity.core.filter

import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.atMost
import com.davils.kore.core.validation.validateOrThrow

private const val SUBJECT: String = "result filter"

/**
 * Decides which results of a call count as failures, the way resilience4j's `recordResult` and `retryOnResult` do:
 * `results { failIf<Response> { response -> response.status >= 500 } }`.
 *
 * A result is a failure when a rule for its type accepts it; a rule for another type never sees it, and `null` is seen
 * only by rules for nullable checks through [ResultFilterBuilder.failIfNull]. A rule is foreign code: [isFailure]
 * throws what it throws, so the call fails with that exception, and a component counts it as a failure. A filter is
 * immutable and safe to share between threads.
 *
 * @since 1.0.0
 */
public class ResultFilter internal constructor(builder: ResultFilterBuilder) : Validatable {
    private val failing: List<ResultRule> = builder.failing.toList()

    /**
     * How many rules the filter has, at most [FailureFilter.HARD_RULES].
     *
     * @since 1.0.0
     */
    public val rules: Int get() = failing.size

    /**
     * Whether the filter has no rule, so that no result is ever a failure.
     *
     * @since 1.0.0
     */
    public val isEmpty: Boolean get() = failing.isEmpty()

    init {
        validateOrThrow(SUBJECT)
    }

    /**
     * Tells whether [result] is a failure.
     *
     * @param result the result of a call.
     * @return true if a rule accepts the result.
     * @throws Throwable what a rule throws.
     * @since 1.0.0
     */
    public fun isFailure(result: Any?): Boolean = failing.any { rule -> rule.accepts(result) }

    /**
     * Checks that the filter has at most [FailureFilter.HARD_RULES] rules.
     *
     * @receiver the validation that collects the violations.
     * @since 1.0.0
     */
    override fun ValidationScope.validate() {
        atMost(property = ::rules, value = rules, maximum = FailureFilter.HARD_RULES)
    }

    /**
     * Shows how many rules the filter has.
     *
     * @return the filter as text.
     * @since 1.0.0
     */
    override fun toString(): String = "ResultFilter(rules=${failing.size})"

    /**
     * The filter without rules.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The filter without rules: no result is a failure.
         *
         * @since 1.0.0
         */
        public val None: ResultFilter = ResultFilter(ResultFilterBuilder())
    }
}
