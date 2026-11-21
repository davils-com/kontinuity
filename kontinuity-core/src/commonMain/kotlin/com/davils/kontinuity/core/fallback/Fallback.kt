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

package com.davils.kontinuity.core.fallback

import com.davils.kontinuity.core.Instance
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kore.core.failure.attempted
import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange

/**
 * Replaces the failure of a call with a value, chosen by the type of the failure: `fallback<User>("user") {
 * on<IOException> { cachedUser() }; onRejected { guestUser() } }`.
 *
 * The handlers are tried in the order they were declared and the first that selects the failure runs; a failure no
 * handler selects is thrown unchanged. A handler is foreign code: when it throws, its exception is thrown with the
 * original failure added as suppressed, unless the handler rethrew the failure itself. The cancellation of the caller
 * and fatal errors are never handled; a cancellation that does not come from the caller reaches the handlers as the
 * `IllegalStateException` that `attempted` of Kore wraps it in. A fallback holds no state and is safe to use from many
 * coroutines at once.
 *
 * @param T the type of the result.
 * @property name the name of the fallback.
 * @param builder the builder that declared the handlers.
 * @since 1.0.0
 */
public class Fallback<T> internal constructor(
    override val name: InstanceName,
    builder: FallbackBuilder<T>,
) : Instance, Validatable {
    private val handlers: List<FallbackHandler<T>> = builder.handlers.toList()

    override val kind: ResilienceKind get() = ResilienceKind.Fallback

    /**
     * How many handlers the fallback has, from 1 to [HARD_HANDLERS].
     *
     * @since 1.0.0
     */
    public val size: Int get() = handlers.size

    /**
     * Runs [block] and returns its result, or the value of the first handler that selects its failure.
     *
     * @param block the call.
     * @return the result of [block] or of a handler.
     * @throws Throwable the failure of [block] when no handler selects it, or the failure of the handler.
     * @since 1.0.0
     */
    public suspend fun execute(block: suspend () -> T): T {
        val result = attempted { block() }
        val failure = result.exceptionOrNull() ?: return result.getOrThrow()
        return recover(failure)
    }

    /**
     * Returns the value of the first handler that selects [failure].
     *
     * @param failure the failure of a call.
     * @return the value of the handler.
     * @throws Throwable [failure] when no handler selects it, or the failure of the handler.
     * @since 1.0.0
     */
    public suspend fun recover(failure: Throwable): T {
        val chosen = handlers.firstNotNullOfOrNull { handler -> chosenOf(handler, failure) } ?: throw failure
        val result = attempted { chosen.handler.handle(chosen.selected) }
        val problem = result.exceptionOrNull() ?: return result.getOrThrow()
        if (problem !== failure) problem.addSuppressed(failure)
        throw problem
    }

    /**
     * Checks that the fallback has from 1 to [HARD_HANDLERS] handlers.
     *
     * @receiver the validation that collects the violations.
     * @since 1.0.0
     */
    override fun ValidationScope.validate() {
        inRange(property = ::size, value = size, range = 1..HARD_HANDLERS)
    }

    /**
     * Shows the name and the number of handlers.
     *
     * @return the fallback as text.
     * @since 1.0.0
     */
    override fun toString(): String = "Fallback($name, handlers=${handlers.size})"

    private fun chosenOf(handler: FallbackHandler<T>, failure: Throwable): Chosen<T>? {
        val selected = handler.select(failure) ?: return null
        return Chosen(handler, selected)
    }

    private data class Chosen<T>(val handler: FallbackHandler<T>, val selected: Throwable)

    /**
     * The hard ceiling of the handlers.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The most handlers of a fallback: 64.
         *
         * @since 1.0.0
         */
        public const val HARD_HANDLERS: Int = 64
    }
}
