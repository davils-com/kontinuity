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

package com.davils.kontinuity.ratelimiter.fixture

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.ratelimiter.RateLimiter
import com.davils.kontinuity.ratelimiter.RateLimiterConfigBuilder
import com.davils.kontinuity.ratelimiter.rateLimiter
import com.davils.kore.core.failure.attempted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal const val LIMITER: String = "search"
internal const val LIMIT: Int = 3
internal val PERIOD: Duration = 1.seconds
internal val TIMEOUT: Duration = 5.seconds

internal fun smallLimiter(
    environment: Environment,
    configure: RateLimiterConfigBuilder.() -> Unit = {
    },
): RateLimiter = rateLimiter(LIMITER, environment) {
    limitForPeriod = LIMIT
    limitRefreshPeriod = PERIOD
    timeout = TIMEOUT
    configure()
}

internal suspend fun withScope(block: suspend (CoroutineScope) -> Unit) {
    val scope = CoroutineScope(currentCoroutineContext() + Job())
    try {
        block(scope)
    } finally {
        scope.cancel()
    }
}

internal suspend fun quietly(block: suspend () -> Unit) {
    attempted { block() }
}
