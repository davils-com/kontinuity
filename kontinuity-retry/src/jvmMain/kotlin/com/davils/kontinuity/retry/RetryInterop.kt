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

package com.davils.kontinuity.retry

import com.davils.kontinuity.retry.internal.BlockingRetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletionStage

/**
 * Runs the blocking [block] and repeats it as the configuration asks, for Java and blocking code; it waits the
 * backoff with `Thread.sleep` on the calling thread.
 *
 * An interrupt of the calling thread, during an attempt or a wait, stops the call at once: the interrupt flag is set
 * again, the [InterruptedException] is thrown and the call counts as cancelled. Other fatal errors pass through.
 *
 * @receiver the retry.
 * @param T the type of the result.
 * @param block the protected call; it may run up to [RetryConfig.maxAttempts] times.
 * @return the first result the result filter accepts, or the last result when the attempts run out or the budget
 *   denies a retry.
 * @throws InterruptedException if the calling thread is interrupted.
 * @throws RetriesExhaustedException if every result failed and [RetryConfig.isFailingAfterMaxAttempts] is set.
 * @throws Throwable the failure of the last attempt, a failure the retry does not repeat, or what a rule of the
 *   result filter throws.
 * @since 1.0.0
 */
public fun <T> Retry.executeBlocking(block: () -> T): T = BlockingRetry(newRun(), block).run()

/**
 * Starts the asynchronous [block] on [scope] and starts it again as the configuration asks, waiting the backoff with
 * `delay` on the dispatcher of [scope].
 *
 * The returned stage completes with the first result the result filter accepts, with the last result, or
 * exceptionally with the failure that ends the call; a failure wrapped in a `CompletionException` is judged by its
 * cause. Cancelling the returned stage, or [scope], stops the call at once, cancels the stage of the running attempt
 * and counts the call as cancelled. kontinuity starts no thread and no timer of its own: the call runs on [scope], so
 * Java code hands in a scope of its own executor, such as `CoroutineScope(executor.asCoroutineDispatcher())`.
 *
 * @receiver the retry.
 * @param T the type of the result.
 * @param scope runs the call and its waits.
 * @param block starts the protected call; it may run up to [RetryConfig.maxAttempts] times.
 * @return the stage of the call.
 * @since 1.0.0
 */
public fun <T> Retry.executeCompletionStage(
    scope: CoroutineScope,
    block: () -> CompletionStage<T>,
): CompletionStage<T> = scope.future { execute { block().await() } }
