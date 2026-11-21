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

package com.davils.kontinuity.timelimiter.internal

import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.timelimiter.TimeoutStrategy
import com.davils.kore.core.failure.attempted
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

internal class SoftLimit(
    private val name: InstanceName,
    private val timeout: Duration,
    private val strategy: TimeoutStrategy.Soft,
    private val work: DetachedWork,
) : Limit {
    override val detachedInFlight: Int get() = work.inFlight

    override suspend fun <T> run(block: suspend () -> T): Completion<T> {
        val call = DetachedCall()
        val deferred = work.start(context = callerContext(), call = call, block = block) ?: return Completion.Rejected(
            strategy.maxDetached,
        )
        return watched(call, deferred)
    }

    private suspend fun callerContext(): CoroutineContext {
        val withoutJob = currentCoroutineContext().minusKey(Job)
        return withoutJob.minusKey(ContinuationInterceptor)
    }

    private suspend fun <T> watched(call: DetachedCall, deferred: Deferred<Result<T>>): Completion<T> {
        var isAbandoned = true
        try {
            val outcome = withTimeoutOrNull(timeout) { awaited(deferred) }
            isAbandoned = outcome == null
            return completionOf(outcome)
        } finally {
            if (isAbandoned) leave(call, deferred)
        }
    }

    private suspend fun <T> awaited(deferred: Deferred<Result<T>>): Result<T> {
        val awaited = attempted { deferred.await() }
        val problem = awaited.exceptionOrNull() ?: return awaited.getOrThrow()
        throw IllegalStateException(
            "The detached call of the time limiter $name was cancelled with the scope of its environment",
            problem,
        )
    }

    private fun <T> completionOf(outcome: Result<T>?): Completion<T> {
        val result = outcome ?: return Completion.TimedOut
        return Completion.Finished(result.getOrThrow())
    }

    private fun leave(call: DetachedCall, deferred: Deferred<*>) {
        val isRunning = call.leave()
        if (!isRunning) work.abandoned(call)
        if (isRunning && strategy.isCancellingOnTimeout) deferred.cancel()
    }
}
