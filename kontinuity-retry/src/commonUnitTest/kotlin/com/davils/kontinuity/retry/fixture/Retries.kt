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

@file:OptIn(ExperimentalKotest::class)

package com.davils.kontinuity.retry.fixture

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.EnvironmentBuilder
import com.davils.kontinuity.core.backoff.fixed
import com.davils.kontinuity.retry.Retry
import com.davils.kontinuity.retry.RetryConfigBuilder
import com.davils.kontinuity.retry.retry
import com.davils.kontinuity.testing.testEnvironment
import com.davils.kore.core.failure.attempted
import io.kotest.common.ExperimentalKotest
import io.kotest.core.test.TestScope
import io.kotest.core.test.testCoroutineScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlin.time.Duration.Companion.seconds

internal const val RETRY: String = "payments"
internal const val BROKEN: String = "broken call"
internal const val ANSWER: Int = 42
internal const val DEFAULT_SEED: Long = 42L
internal val WAIT = 1.seconds

internal fun TestScope.virtualEnvironment(
    seed: Long = DEFAULT_SEED,
    configure: EnvironmentBuilder.() -> Unit = {},
): Environment = testEnvironment(timeSource = testCoroutineScheduler.timeSource, seed = seed, configure = configure)

internal fun quickRetry(environment: Environment, configure: RetryConfigBuilder.() -> Unit = {}): Retry =
    retry(RETRY, environment) {
        backoff = fixed(WAIT)
        configure()
    }

internal fun broken(): Nothing = error(BROKEN)

internal suspend fun quietly(block: suspend () -> Unit) {
    attempted { block() }
}

internal suspend fun withScope(block: suspend (CoroutineScope) -> Unit) {
    val scope = CoroutineScope(currentCoroutineContext() + Job())
    try {
        block(scope)
    } finally {
        scope.cancel()
    }
}
