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

import com.davils.kontinuity.core.CallRejectedException
import com.davils.kontinuity.core.CallTimeoutException
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.causeChain
import com.davils.kontinuity.core.rejectionOrNull
import com.davils.kore.core.annotation.DavilsDsl
import com.davils.kore.core.dsl.ValidatingBuilder

/**
 * Builds a [Fallback]; its handlers are tried in the order they are declared.
 *
 * @param T the type of the result.
 * @param name the name of the fallback.
 * @since 1.0.0
 */
@DavilsDsl
public class FallbackBuilder<T> internal constructor(
    private val name: InstanceName,
) : ValidatingBuilder<Fallback<T>>() {
    override val subject: String get() = "fallback"

    internal val handlers: MutableList<FallbackHandler<T>> = mutableListOf()

    /**
     * Handles failures of the type [E] and its subtypes with [handler].
     *
     * @param E the type of failure.
     * @param handler returns the value in place of the failure; it is foreign code.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public inline fun <reified E : Throwable> on(noinline handler: suspend (E) -> T) {
        addHandler(select = { failure -> failure as? E }) { selected -> handler(selected as E) }
    }

    /**
     * Handles every failure with a `CallRejectedException` in its cause chain with [handler], which receives the
     * rejection.
     *
     * @param handler returns the value in place of the failure; it is foreign code.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun onRejected(handler: suspend (CallRejectedException) -> T) {
        addHandler(select = { failure -> failure.rejectionOrNull() }) { selected ->
            handler(selected as CallRejectedException)
        }
    }

    /**
     * Handles every failure with a [CallTimeoutException] in its cause chain with [handler], which receives the
     * timeout.
     *
     * @param handler returns the value in place of the failure; it is foreign code.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun onTimeout(handler: suspend (CallTimeoutException) -> T) {
        addHandler(select = ::timeoutOf) { selected -> handler(selected as CallTimeoutException) }
    }

    /**
     * Handles every failure that no earlier handler selects with [handler].
     *
     * @param handler returns the value in place of the failure; it is foreign code.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun otherwise(handler: suspend (Throwable) -> T) {
        addHandler(select = { failure -> failure }, handle = handler)
    }

    @PublishedApi
    internal fun addHandler(select: (Throwable) -> Throwable?, handle: suspend (Throwable) -> T) {
        checkOpen()
        handlers += FallbackHandler(select, handle)
    }

    override fun assemble(): Fallback<T> = Fallback(name, this)

    private companion object {
        fun timeoutOf(failure: Throwable): Throwable? = failure.causeChain().firstOrNull { cause ->
            cause is CallTimeoutException
        }
    }
}
