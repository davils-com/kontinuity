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

package com.davils.kontinuity.circuitbreaker.fixture

import com.davils.kontinuity.circuitbreaker.CircuitBreaker
import com.davils.kontinuity.circuitbreaker.CircuitBreakerConfigBuilder
import com.davils.kontinuity.circuitbreaker.circuitBreaker
import com.davils.kontinuity.circuitbreaker.countBased
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.backoff.fixed
import com.davils.kore.core.failure.attempted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlin.time.Duration.Companion.seconds

internal const val BREAKER: String = "payments"
internal const val WINDOW: Int = 4
internal val WAIT = 10.seconds

internal fun smallBreaker(
    environment: Environment,
    configure: CircuitBreakerConfigBuilder.() -> Unit = {
    },
): CircuitBreaker = circuitBreaker(BREAKER, environment) {
    slidingWindow = countBased(WINDOW)
    minimumNumberOfCalls = WINDOW
    permittedCallsInHalfOpenState = 2
    waitInOpenState = fixed(WAIT)
    configure()
}

internal suspend fun CircuitBreaker.fail(times: Int = 1) {
    repeat(times) { quietly { execute { throw IllegalStateException("broken call") } } }
}

internal suspend fun CircuitBreaker.succeed(times: Int = 1) {
    repeat(times) { execute { } }
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
