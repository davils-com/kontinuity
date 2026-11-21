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

import com.davils.kontinuity.core.causeChain
import com.davils.kontinuity.core.rejectionOrNull
import com.davils.kore.core.failure.contained
import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.atMost
import com.davils.kore.core.validation.validateOrThrow
import kotlin.reflect.KClass

private const val SUBJECT: String = "failure filter"

/**
 * Decides which failures of a call count, the way resilience4j's `recordExceptions` and `ignoreExceptions` do.
 *
 * The decision for a failure runs in this order:
 *
 * 1. A failure with a `CallRejectedException` in its cause chain is [FailureVerdict.Ignored], always: a rejection is
 *    never a failure of another component.
 * 2. A failure that an ignore rule matches is [FailureVerdict.Ignored]; ignore rules win over record rules.
 * 3. A failure that a record rule matches is [FailureVerdict.Recorded].
 * 4. Any other failure is [FailureVerdict.Recorded] when the filter has no record rule, and [FailureVerdict.Passed]
 *    otherwise.
 *
 * A type rule matches a failure of that type or a subtype; with [isCauseChainSearched] it also matches when a cause in
 * the chain, at most 32 deep, has that type. A predicate rule is foreign code and runs contained: a predicate that
 * throws makes the failure [FailureVerdict.Recorded] and its exception is added to the failure as suppressed, so
 * nothing is lost; fatal errors are rethrown. A filter is immutable and safe to share between threads.
 *
 * @since 1.0.0
 */
public class FailureFilter internal constructor(builder: FailureFilterBuilder) : Validatable {
    private val ignored: List<FailureRule> = builder.ignored.toList()
    private val recorded: List<FailureRule> = builder.recorded.toList()

    /**
     * Whether a type rule also looks at the causes of a failure: true by default.
     *
     * @since 1.0.0
     */
    public val isCauseChainSearched: Boolean = builder.isCauseChainSearched

    /**
     * How many rules the filter has, at most [HARD_RULES].
     *
     * @since 1.0.0
     */
    public val rules: Int get() = ignored.size + recorded.size

    init {
        validateOrThrow(SUBJECT)
    }

    /**
     * Decides about [failure], as described at [FailureFilter].
     *
     * @param failure the failure of a call.
     * @return the verdict.
     * @since 1.0.0
     */
    public fun verdictOf(failure: Throwable): FailureVerdict {
        val decided = contained { decide(failure) }
        val problem = decided.exceptionOrNull() ?: return decided.getOrThrow()
        failure.addSuppressed(problem)
        return FailureVerdict.Recorded
    }

    private fun decide(failure: Throwable): FailureVerdict {
        val isIgnored = failure.rejectionOrNull() != null || matchesAny(ignored, failure)
        if (isIgnored) return FailureVerdict.Ignored
        return recordedVerdictOf(failure)
    }

    /**
     * Checks that the filter has at most [HARD_RULES] rules.
     *
     * @receiver the validation that collects the violations.
     * @since 1.0.0
     */
    override fun ValidationScope.validate() {
        atMost(property = ::rules, value = rules, maximum = HARD_RULES)
    }

    /**
     * Shows how many rules of each kind the filter has.
     *
     * @return the filter as text.
     * @since 1.0.0
     */
    override fun toString(): String =
        "FailureFilter(ignored=${ignored.size}, recorded=${recorded.size}, causes=$isCauseChainSearched)"

    private fun recordedVerdictOf(failure: Throwable): FailureVerdict {
        if (recorded.isEmpty() || matchesAny(recorded, failure)) return FailureVerdict.Recorded
        return FailureVerdict.Passed
    }

    private fun matchesAny(rules: List<FailureRule>, failure: Throwable): Boolean = rules.any { rule ->
        matches(rule, failure)
    }

    private fun matches(rule: FailureRule, failure: Throwable): Boolean = when (rule) {
        is FailureRule.OfType -> matchesType(rule.type, failure)
        is FailureRule.Matching -> rule.predicate(failure)
    }

    private fun matchesType(type: KClass<out Throwable>, failure: Throwable): Boolean {
        if (!isCauseChainSearched) return type.isInstance(failure)
        return failure.causeChain().any { cause -> type.isInstance(cause) }
    }

    /**
     * The hard ceiling of the rules and the filters every component starts from.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The most rules of a filter: 64.
         *
         * @since 1.0.0
         */
        public const val HARD_RULES: Int = 64

        /**
         * The filter that records every failure but the rejections of kontinuity.
         *
         * @since 1.0.0
         */
        public val RecordAll: FailureFilter = FailureFilter(FailureFilterBuilder())
    }
}
