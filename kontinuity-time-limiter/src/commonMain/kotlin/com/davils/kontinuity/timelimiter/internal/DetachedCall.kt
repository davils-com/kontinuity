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

package com.davils.kontinuity.timelimiter.internal

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal class DetachedCall {
    private val phase = AtomicInt(WATCHED)
    private val ending = AtomicReference<Throwable?>(null)

    val failure: Throwable? get() = ending.load()

    fun ended(result: Result<*>) {
        ending.store(result.exceptionOrNull())
    }

    fun leave(): Boolean = phase.compareAndSet(expectedValue = WATCHED, newValue = LEFT)

    fun finish(): Boolean = phase.exchange(FINISHED) == LEFT

    private companion object {
        const val WATCHED: Int = 0
        const val LEFT: Int = 1
        const val FINISHED: Int = 2
    }
}
