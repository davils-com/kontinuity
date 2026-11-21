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

package com.davils.kontinuity.bulkhead

import com.davils.kore.core.failure.attempted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Runs the blocking [block] on the dispatcher of the bulkhead, for Java and blocking code, the counterpart of
 * `submit` of the thread pool bulkhead of resilience4j.
 *
 * The call is admitted or refused before this function returns: a refusal completes the returned stage exceptionally
 * with a [BulkheadFullException]. The call is a child of [scope], so cancelling the scope cancels a call that has not
 * started yet, which is counted as cancelled; cancelling the returned stage does the same for this call alone. A
 * failure of [block] completes the stage exceptionally and never cancels [scope]; fatal errors pass through to it.
 *
 * @receiver the bulkhead.
 * @param T the type of the result.
 * @param scope the scope of the caller that owns the call.
 * @param block the protected blocking call; it runs on a thread of the dispatcher of the bulkhead.
 * @return the stage of the call.
 * @since 1.0.0
 */
public fun <T> DispatcherBulkhead.submit(scope: CoroutineScope, block: () -> T): CompletionStage<T> {
    val stage = CompletableFuture<T>()
    val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        val outcome = attempted { execute { block() } }
        outcome.onSuccess { value -> stage.complete(value) }
        outcome.onFailure { failure -> stage.completeExceptionally(failure) }
    }
    job.invokeOnCompletion { cause -> cause?.let(stage::completeExceptionally) }
    stage.whenComplete { _, _ -> if (stage.isCancelled) job.cancel() }
    return stage
}
