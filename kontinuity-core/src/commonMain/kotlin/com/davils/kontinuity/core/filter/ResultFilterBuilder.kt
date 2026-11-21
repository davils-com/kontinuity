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

package com.davils.kontinuity.core.filter

import com.davils.kore.core.annotation.DavilsDsl
import com.davils.kore.core.dsl.ValidatingBuilder
import kotlin.reflect.KClass

/**
 * Builds a [ResultFilter]: `results { failIf<Response> { response -> response.status >= 500 } }`.
 *
 * @since 1.0.0
 */
@DavilsDsl
public class ResultFilterBuilder internal constructor() : ValidatingBuilder<ResultFilter>() {
    override val subject: String get() = "result filter"

    internal val failing: MutableList<ResultRule> = mutableListOf()

    /**
     * Makes a result of the type [T] a failure when [predicate] accepts it.
     *
     * @param T the type of result.
     * @param predicate decides about a result; it is foreign code.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public inline fun <reified T : Any> failIf(noinline predicate: (T) -> Boolean) {
        addRule(T::class) { result -> result is T && predicate(result) }
    }

    /**
     * Makes a `null` result a failure.
     *
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun failIfNull() {
        checkOpen()
        failing += ResultRule(type = null) { false }
    }

    @PublishedApi
    internal fun addRule(type: KClass<*>, predicate: (Any) -> Boolean) {
        checkOpen()
        failing += ResultRule(type, predicate)
    }

    override fun assemble(): ResultFilter = ResultFilter(this)
}
