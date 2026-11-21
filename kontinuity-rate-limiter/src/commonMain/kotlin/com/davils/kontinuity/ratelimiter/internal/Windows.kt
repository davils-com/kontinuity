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

package com.davils.kontinuity.ratelimiter.internal

import com.davils.kontinuity.ratelimiter.RateLimiterAlgorithm
import com.davils.kontinuity.ratelimiter.RateLimiterConfig

internal fun windowOf(config: RateLimiterConfig): PermitWindow {
    val periodNanos = config.limitRefreshPeriod.inWholeNanoseconds
    return when (val algorithm = config.algorithm) {
        RateLimiterAlgorithm.FixedWindow -> FixedWindow(periodNanos, config.limitForPeriod)

        RateLimiterAlgorithm.SlidingWindow -> SlidingLog(periodNanos, config.maxWaitingCallers)

        is RateLimiterAlgorithm.TokenBucket -> TokenBucket(
            periodNanos = periodNanos,
            burst = algorithm.burst,
            initialLimit = config.limitForPeriod,
        )
    }
}
