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

package com.davils.kontinuity.core

import com.davils.kore.core.failure.attempted
import com.davils.kore.core.outcome.Outcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow

/**
 * Runs [block] under the protection of this stage, the operator form of [Stage.execute]: `breaker { call() }`.
 *
 * @receiver the stage that protects the call.
 * @param T the type of the result.
 * @param block the protected call.
 * @return the result of [block].
 * @throws CallRejectedException if the stage refuses the call.
 * @throws Throwable whatever [block] throws and the stage passes on.
 * @since 1.0.0
 */
public suspend operator fun <T> Stage.invoke(block: suspend () -> T): T = execute(block)

/**
 * Runs [block] under the protection of this stage and reports how it ended instead of throwing.
 *
 * The cancellation of the caller and fatal errors, such as `OutOfMemoryError` on the JVM, are still thrown. A
 * cancellation that does not come from the caller, such as the timeout of a callee, is a [CallFailure.Failed] whose
 * cause is an `IllegalStateException` wrapping it, as `attempted` of Kore reports it.
 *
 * @receiver the stage that protects the call.
 * @param T the type of the result.
 * @param block the protected call.
 * @return the result, or the failure or the rejection the call ended with.
 * @since 1.0.0
 */
public suspend fun <T> Stage.attempt(block: suspend () -> T): Outcome<T, CallFailure> {
    val result = attempted { execute(block) }
    val failure = result.exceptionOrNull() ?: return Outcome.Success(result.getOrThrow())
    return Outcome.Failure(callFailureOf(failure))
}

private fun callFailureOf(failure: Throwable): CallFailure {
    val rejection = failure as? CallRejectedException ?: return CallFailure.Failed(failure)
    return CallFailure.Rejected(rejection)
}

/**
 * Returns a function that runs [block] under the protection of this stage each time it is called.
 *
 * @receiver the stage that protects the calls.
 * @param T the type of the result.
 * @param block the call to protect.
 * @return the protected call.
 * @since 1.0.0
 */
public fun <T> Stage.decorate(block: suspend () -> T): suspend () -> T = { execute(block) }

/**
 * Returns a function that runs [function] under the protection of this stage each time it is called.
 *
 * @receiver the stage that protects the calls.
 * @param A the type of the argument.
 * @param R the type of the result.
 * @param function the function to protect.
 * @return the protected function.
 * @since 1.0.0
 */
public fun <A, R> Stage.decorateFunction(function: suspend (A) -> R): suspend (A) -> R = { argument ->
    execute { function(argument) }
}

/**
 * Returns a function that runs [function] under the protection of this stage each time it is called.
 *
 * @receiver the stage that protects the calls.
 * @param A the type of the first argument.
 * @param B the type of the second argument.
 * @param R the type of the result.
 * @param function the function to protect.
 * @return the protected function.
 * @since 1.0.0
 */
public fun <A, B, R> Stage.decorateFunction(function: suspend (A, B) -> R): suspend (A, B) -> R = { first, second ->
    execute { function(first, second) }
}

/**
 * Returns a flow that collects this flow under the protection of [stage]: the whole collection is one call.
 *
 * A stage that repeats calls, such as a retry, collects this flow again from the start, so a collector may see an
 * element twice; a stage that refuses the call ends the flow with its [CallRejectedException]. The elements pass a
 * rendezvous channel, so a stage may run the collection in another coroutine, such as a soft time limiter, without
 * buffering elements.
 *
 * @receiver the flow to protect.
 * @param T the type of the elements.
 * @param stage the stage that protects the collection.
 * @return the protected flow.
 * @since 1.0.0
 */
public fun <T> Flow<T>.through(stage: Stage): Flow<T> {
    val upstream = this
    val protected = channelFlow {
        stage.execute { upstream.collect { element -> send(element) } }
    }
    return protected.buffer(Channel.RENDEZVOUS)
}
