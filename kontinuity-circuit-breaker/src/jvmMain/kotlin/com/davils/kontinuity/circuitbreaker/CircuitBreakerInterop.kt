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

package com.davils.kontinuity.circuitbreaker

import com.davils.kore.core.failure.rethrowIfFatal
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

/**
 * Runs the blocking [block] if the breaker permits it and records its outcome, for Java and blocking code.
 *
 * Fatal errors release the permit and pass through.
 *
 * @receiver the breaker.
 * @param T the type of the result.
 * @param block the protected call.
 * @return the result of [block].
 * @throws CallNotPermittedException if the breaker refuses the call.
 * @throws Throwable what [block] throws, or what a rule of the result filter throws.
 * @since 1.0.0
 */
public fun <T> CircuitBreaker.executeBlocking(block: () -> T): T {
    val permit = acquirePermission()
    try {
        val result = runCatching(block)
        result.onFailure { failure ->
            failure.rethrowIfFatal()
            permit.onFailure(failure)
        }
        val value = result.getOrThrow()
        permit.onResult(value)
        return value
    } finally {
        permit.release()
    }
}

/**
 * Starts the asynchronous [block] if the breaker permits it and records the outcome of its stage when it completes.
 *
 * A refusal completes the returned stage exceptionally with a [CallNotPermittedException] instead of throwing.
 *
 * @receiver the breaker.
 * @param T the type of the result.
 * @param block starts the protected call.
 * @return the stage of the call.
 * @since 1.0.0
 */
public fun <T> CircuitBreaker.executeCompletionStage(block: () -> CompletionStage<T>): CompletionStage<T> {
    val permit = tryAcquirePermission() ?: return CompletableFuture.failedFuture(CallNotPermittedException(name, state))
    val started = runCatching(block)
    started.exceptionOrNull()?.rethrowIfFatal()
    val stage = started.getOrElse { failure -> CompletableFuture.failedFuture(failure) }
    return stage.whenComplete { value, failure ->
        val cause = (failure as? CompletionException)?.cause ?: failure
        if (cause == null) permit.onResult(value)
        if (cause != null) permit.onFailure(cause)
    }
}
