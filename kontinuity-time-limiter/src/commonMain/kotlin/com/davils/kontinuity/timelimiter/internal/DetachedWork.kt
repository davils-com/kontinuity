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

import com.davils.kore.core.failure.attempted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext

internal class DetachedWork(
    private val scope: CoroutineScope,
    private val maxDetached: Int,
    private val reporter: Reporter,
) {
    private val held = AtomicInt(0)

    val inFlight: Int get() = held.load()

    fun <T> start(context: CoroutineContext, call: DetachedCall, block: suspend () -> T): Deferred<Result<T>>? {
        if (!reserve()) return null
        reporter.detached(held.load())
        val deferred = scope.async(context) {
            val result = attempted { block() }
            call.ended(result)
            result
        }
        deferred.invokeOnCompletion { finished(call) }
        return deferred
    }

    fun abandoned(call: DetachedCall) {
        val failure = call.failure ?: return
        reporter.detachedFailed(failure)
    }

    private fun finished(call: DetachedCall) {
        held.addAndFetch(-1)
        reporter.detached(held.load())
        if (call.finish()) abandoned(call)
    }

    private fun reserve(): Boolean {
        var current = held.load()
        while (current < maxDetached) {
            if (held.compareAndSet(expectedValue = current, newValue = current + 1)) return true
            current = held.load()
        }
        return false
    }
}
