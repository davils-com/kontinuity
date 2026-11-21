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

@file:OptIn(ExperimentalAtomicApi::class)

package com.davils.kontinuity.ratelimiter.internal

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal fun <S : Any> AtomicReference<S>.swap(next: (S) -> S): S {
    while (true) {
        val current = load()
        if (compareAndSet(expectedValue = current, newValue = next(current))) return current
    }
}

internal fun <S : Any> AtomicReference<S>.trySwap(next: (S) -> S?): Boolean {
    var isSwapped = false
    swap { current ->
        val proposed = next(current)
        isSwapped = proposed != null
        proposed ?: current
    }
    return isSwapped
}
