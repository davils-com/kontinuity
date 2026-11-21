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

package com.davils.kontinuity.cache.fixture

import com.davils.kontinuity.cache.Cache
import com.davils.kontinuity.cache.CacheBuilder
import com.davils.kontinuity.cache.cache
import com.davils.kontinuity.core.Environment
import com.davils.kore.core.failure.attempted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext

internal const val CACHE: String = "users"
internal const val KEY: String = "alice"
internal const val OTHER_KEY: String = "bob"
internal const val VALUE: String = "Alice"
internal const val NEWER_VALUE: String = "Alice Newer"
internal const val LOADED_VALUE: String = "Alice Loaded"

internal fun smallCache(
    environment: Environment,
    configure: CacheBuilder<String, String>.() -> Unit = {
    },
): Cache<String, String> = cache(CACHE, environment, configure)

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

internal suspend fun failureOf(block: suspend () -> Unit): Throwable? = attempted { block() }.exceptionOrNull()
