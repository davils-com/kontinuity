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

import com.davils.kore.core.annotation.DavilsDsl
import com.davils.kore.core.dsl.ValidatingBuilder
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Builds a [RetryBudget]; every property starts from the default of Finagle and gRPC.
 *
 * @property timeSource the time of the budget, from its environment.
 * @since 1.0.0
 */
@DavilsDsl
public class RetryBudgetBuilder internal constructor(
    internal val timeSource: TimeSource.WithComparableMarks,
) : ValidatingBuilder<RetryBudget>() {
    override val subject: String get() = "retry budget"

    /**
     * See [RetryBudget.ratio].
     *
     * @since 1.0.0
     */
    public var ratio: Double = RetryBudget.DEFAULT_RATIO
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RetryBudget.minRetriesPerSecond].
     *
     * @since 1.0.0
     */
    public var minRetriesPerSecond: Double = RetryBudget.DEFAULT_MIN_RETRIES_PER_SECOND
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [RetryBudget.window].
     *
     * @since 1.0.0
     */
    public var window: Duration = RetryBudget.DEFAULT_WINDOW
        set(value) {
            checkOpen()
            field = value
        }

    override fun assemble(): RetryBudget = RetryBudget(this)
}
