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

import kotlinx.coroutines.CompletableDeferred

internal class Gate<T> {
    private val opened = CompletableDeferred<T>()

    var entered: Int = 0
        private set

    suspend fun pass(): T {
        entered += 1
        return opened.await()
    }

    fun open(value: T) {
        opened.complete(value)
    }

    fun fail(failure: Throwable) {
        opened.completeExceptionally(failure)
    }
}
