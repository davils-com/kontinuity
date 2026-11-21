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

package com.davils.kontinuity.timelimiter.fixture

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.timelimiter.TimeLimiter
import com.davils.kontinuity.timelimiter.TimeLimiterConfigBuilder
import com.davils.kontinuity.timelimiter.TimeoutStrategy
import com.davils.kontinuity.timelimiter.timeLimiter
import com.davils.kore.core.failure.attempted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

internal const val LIMITER: String = "payments"
internal const val RESULT: String = "paid"
internal val TIMEOUT = 1.seconds
internal val SHORT = TIMEOUT / 2
internal val LONG = TIMEOUT * 10

internal fun hardLimiter(
    environment: Environment,
    configure: TimeLimiterConfigBuilder.() -> Unit = {
    },
): TimeLimiter = timeLimiter(LIMITER, environment) {
    timeout = TIMEOUT
    configure()
}

internal fun softLimiter(
    environment: Environment,
    isCancellingOnTimeout: Boolean = true,
    maxDetached: Int = TimeoutStrategy.DEFAULT_MAX_DETACHED,
): TimeLimiter = timeLimiter(LIMITER, environment) {
    timeout = TIMEOUT
    strategy = TimeoutStrategy.Soft(isCancellingOnTimeout = isCancellingOnTimeout, maxDetached = maxDetached)
}

internal suspend fun TimeLimiter.slow(): String = execute {
    delay(LONG)
    RESULT
}

internal suspend fun TimeLimiter.quick(): String = execute {
    delay(SHORT)
    RESULT
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
