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

package com.davils.kontinuity.bulkhead.fixture

import com.davils.kontinuity.bulkhead.Bulkhead
import com.davils.kontinuity.bulkhead.BulkheadConfigBuilder
import com.davils.kontinuity.bulkhead.BulkheadSnapshot
import com.davils.kontinuity.bulkhead.DispatcherBulkheadSnapshot
import com.davils.kontinuity.bulkhead.bulkhead
import com.davils.kontinuity.core.Environment
import com.davils.kore.core.failure.attempted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlin.time.Duration.Companion.minutes

internal const val BULKHEAD: String = "payments"
internal val LONG_WAIT = 1.minutes

internal fun waitingBulkhead(
    environment: Environment,
    limit: Int = 1,
    configure: BulkheadConfigBuilder.() -> Unit = {
    },
): Bulkhead = bulkhead(BULKHEAD, environment) {
    maxConcurrentCalls = limit
    maxWaitDuration = LONG_WAIT
    configure()
}

internal val BulkheadSnapshot.decided: Long get() = permitted + rejected + cancelled

internal val DispatcherBulkheadSnapshot.decided: Long get() = permitted + rejected + cancelled + queued

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
