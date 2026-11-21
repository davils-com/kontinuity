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
 * Builds a [FailureFilter]: `failures { record<IOException>(); ignore<BusinessException>() }`.
 *
 * @since 1.0.0
 */
@DavilsDsl
public class FailureFilterBuilder internal constructor() : ValidatingBuilder<FailureFilter>() {
    override val subject: String get() = "failure filter"

    internal val ignored: MutableList<FailureRule> = mutableListOf()
    internal val recorded: MutableList<FailureRule> = mutableListOf()

    /**
     * See [FailureFilter.isCauseChainSearched].
     *
     * @since 1.0.0
     */
    public var isCauseChainSearched: Boolean = true
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * Records failures of [type] and its subtypes; once a filter has a record rule, it records only what its record
     * rules match.
     *
     * @param type the type of failure.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun record(type: KClass<out Throwable>) {
        checkOpen()
        recorded += FailureRule.OfType(type)
    }

    /**
     * Records failures of the type [T] and its subtypes, like [record] with a class.
     *
     * @param T the type of failure.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public inline fun <reified T : Throwable> record() {
        record(T::class)
    }

    /**
     * Records the failures [predicate] accepts; the predicate is foreign code and runs contained.
     *
     * @param predicate decides about a failure.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun recordIf(predicate: (Throwable) -> Boolean) {
        checkOpen()
        recorded += FailureRule.Matching(predicate)
    }

    /**
     * Ignores failures of [type] and its subtypes; ignore rules win over record rules.
     *
     * @param type the type of failure.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun ignore(type: KClass<out Throwable>) {
        checkOpen()
        ignored += FailureRule.OfType(type)
    }

    /**
     * Ignores failures of the type [T] and its subtypes, like [ignore] with a class.
     *
     * @param T the type of failure.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public inline fun <reified T : Throwable> ignore() {
        ignore(T::class)
    }

    /**
     * Ignores the failures [predicate] accepts; the predicate is foreign code and runs contained.
     *
     * @param predicate decides about a failure.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun ignoreIf(predicate: (Throwable) -> Boolean) {
        checkOpen()
        ignored += FailureRule.Matching(predicate)
    }

    override fun assemble(): FailureFilter = FailureFilter(this)
}
