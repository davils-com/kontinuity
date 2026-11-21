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

import com.davils.kontinuity.retry.internal.BudgetLedger
import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.validateOrThrow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private const val SUBJECT: String = "retry budget"

/**
 * A budget of retries, shared by any number of [Retry] instances, that bounds the retries to a share of the calls,
 * as the retry budgets of Finagle and gRPC do: `retryBudget { ratio = 0.2; minRetriesPerSecond = 10.0 }`.
 *
 * Every call of a retry that uses the budget deposits one token, every retry withdraws one. A retry is granted while
 * `minRetriesPerSecond * window + ratio * calls - retries` is at least 1, counting the calls and the retries of the
 * last [window] in ten slots; when the budget denies a retry, the call ends with its last failure or its last result.
 * A budget thus keeps a fleet of clients from multiplying the load of a struggling service by its attempts.
 *
 * A budget is safe to use from many coroutines and threads at once. Its time comes from the environment it was
 * created with, so that a test drives it with virtual time. Reading [balance] changes nothing. Every value is bounded
 * by a hard ceiling; a value out of its bounds is refused when the budget is built.
 *
 * @since 1.0.0
 */
public class RetryBudget internal constructor(builder: RetryBudgetBuilder) : Validatable {
    /**
     * The share of the calls that may be retried: 0.2 by default, from 0 to [HARD_RATIO].
     *
     * @since 1.0.0
     */
    public val ratio: Double = builder.ratio

    /**
     * The retries per second granted on top of [ratio], even without calls: 10 by default, from 0 to
     * [HARD_MIN_RETRIES_PER_SECOND].
     *
     * @since 1.0.0
     */
    public val minRetriesPerSecond: Double = builder.minRetriesPerSecond

    /**
     * How far back the budget counts calls and retries: ten seconds by default, positive and at most [HARD_WINDOW].
     *
     * @since 1.0.0
     */
    public val window: Duration = builder.window

    init {
        validateOrThrow(SUBJECT)
    }

    private val ledger: BudgetLedger = BudgetLedger(
        ratio = ratio,
        minRetriesPerSecond = minRetriesPerSecond,
        window = window,
        timeSource = builder.timeSource,
    )

    /**
     * How many retries the budget would grant at this moment, never below 0; reading it changes nothing.
     *
     * @since 1.0.0
     */
    public val balance: Long get() = ledger.balance()

    /**
     * Checks every value against its bounds.
     *
     * @receiver the validation that collects the violations.
     * @since 1.0.0
     */
    override fun ValidationScope.validate() {
        inRange(property = ::ratio, value = ratio, range = 0.0..HARD_RATIO)
        inRange(property = ::minRetriesPerSecond, value = minRetriesPerSecond, range = 0.0..HARD_MIN_RETRIES_PER_SECOND)
        if (!window.isPositive()) fail(property = ::window, message = "must be positive")
        inRange(property = ::window, value = window, range = Duration.ZERO..HARD_WINDOW)
    }

    /**
     * Shows the settings and the balance.
     *
     * @return the budget as text.
     * @since 1.0.0
     */
    override fun toString(): String =
        "RetryBudget(ratio=$ratio, minRetriesPerSecond=$minRetriesPerSecond, window=$window, balance=$balance)"

    internal fun deposit() {
        ledger.deposit()
    }

    internal fun tryWithdraw(): Boolean = ledger.tryWithdraw()

    /**
     * The hard ceilings and the defaults of a budget.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The largest share of the calls that may be retried: 10, that is ten retries per call.
         *
         * @since 1.0.0
         */
        public const val HARD_RATIO: Double = 10.0

        /**
         * The most retries per second a budget grants without calls: 10,000.
         *
         * @since 1.0.0
         */
        public const val HARD_MIN_RETRIES_PER_SECOND: Double = 10_000.0

        /**
         * The longest window of a budget: one hour.
         *
         * @since 1.0.0
         */
        public val HARD_WINDOW: Duration = 1.hours

        internal const val DEFAULT_RATIO: Double = 0.2
        internal const val DEFAULT_MIN_RETRIES_PER_SECOND: Double = 10.0
        internal val DEFAULT_WINDOW: Duration = 10.seconds
    }
}
