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

@file:OptIn(DavilsInternal::class)

package com.davils.kontinuity.timelimiter

import com.davils.kontinuity.timelimiter.internal.Interruption
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.concurrency.blocking
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture

/**
 * Starts the future of [block] and waits for it within the timeout, as `decorateFutureSupplier` of resilience4j does.
 *
 * [block] runs where the strategy runs the call: in the coroutine of the caller for a hard limiter, detached on the
 * scope for a soft one. When the caller stops waiting, at the timeout or by its own cancellation, the future is
 * cancelled if the strategy cancels the call: always for [TimeoutStrategy.Hard], and for [TimeoutStrategy.Soft] when
 * [TimeoutStrategy.Soft.isCancellingOnTimeout] is set; otherwise it runs on to its end. Cancelling a
 * `CompletableFuture` does not interrupt the thread that completes it. A future that is cancelled by someone else is
 * a failure of the call. Counting and events follow [TimeLimiter.execute].
 *
 * @receiver the limiter.
 * @param T the type of the result.
 * @param block starts the call and returns its future.
 * @return the result of the future.
 * @throws com.davils.kontinuity.core.CallTimeoutException if the future does not complete in time.
 * @throws DetachedLimitExceededException if a soft limiter holds as many detached calls as it may.
 * @throws IllegalStateException if the future was cancelled by someone else; the cancellation is its cause.
 * @throws Throwable what [block] throws or the future completes with, unwrapped from a `CompletionException`.
 * @since 1.0.0
 */
public suspend fun <T> TimeLimiter.executeFuture(block: () -> CompletableFuture<T>): T = execute {
    val future = block()
    future.await()
}

/**
 * Runs the blocking [block] on [dispatcher] within the timeout and interrupts its thread when the call is cancelled.
 *
 * The call is cancelled at the timeout of a hard limiter, at the timeout of a soft limiter that cancels its calls and
 * by the cancellation of the caller. The thread of [block] is then interrupted, once and only while [block] runs, so
 * an interrupt never reaches other work of [dispatcher]; the interrupt flag that kontinuity set is cleared before the
 * thread returns to [dispatcher]. A call that ignores interrupts keeps a hard limiter waiting until it returns, so the
 * timeout reaches the caller that much later. kontinuity never chooses a dispatcher: hand in one meant for blocking
 * work, such as `Dispatchers.IO`. Counting and events follow [TimeLimiter.execute].
 *
 * @receiver the limiter.
 * @param T the type of the result.
 * @param dispatcher where [block] runs.
 * @param block the blocking call.
 * @return the result of [block].
 * @throws com.davils.kontinuity.core.CallTimeoutException if [block] does not return in time.
 * @throws DetachedLimitExceededException if a soft limiter holds as many detached calls as it may.
 * @throws IllegalStateException if [dispatcher] refuses the call or [block] throws a cancellation of its own.
 * @throws Throwable what [block] throws.
 * @since 1.0.0
 */
public suspend fun <T> TimeLimiter.executeBlocking(dispatcher: CoroutineDispatcher, block: () -> T): T = execute {
    val interruption = Interruption()
    blocking(dispatcher = dispatcher, onCancel = interruption::interrupt) { interruption.run(block) }
}
