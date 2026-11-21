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

package com.davils.kontinuity.hedge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import java.util.concurrent.CompletionStage

/**
 * Hedges the asynchronous [block] for Java and future-based code: it starts the stage of the primary at once and,
 * while it is slow or fails, the stages of the hedged copies, and completes with the first stage that succeeds.
 *
 * The hedging runs as a coroutine of [scope], on its dispatcher; kontinuity starts no thread of its own. When a
 * copy wins, the stages of the other copies are cancelled through `CompletableFuture.cancel`, which stops the work
 * behind them only if their producer reacts to it. Cancelling the returned stage cancels the call and every copy, and
 * so does the cancellation of [scope]. A failure that [block] throws instead of returning a stage is a failure of
 * that copy.
 *
 * @receiver the hedge.
 * @param T the type of the result.
 * @param scope the scope the hedging runs in; the hedge never chooses a dispatcher.
 * @param block starts one copy of the protected call; it runs up to [HedgeConfig.maxHedgedAttempts] + 1 times and
 *   must be safe to run in parallel.
 * @return the stage of the call, which completes with the result of the first copy that succeeded, or exceptionally
 *   with the failure [Hedge.execute] throws.
 * @since 1.0.0
 */
public fun <T> Hedge.executeCompletionStage(
    scope: CoroutineScope,
    block: () -> CompletionStage<T>,
): CompletionStage<T> = scope.future { execute { block().await() } }
