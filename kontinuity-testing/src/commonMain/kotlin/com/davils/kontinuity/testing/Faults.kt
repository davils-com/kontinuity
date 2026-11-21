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

@file:OptIn(ExperimentalAtomicApi::class)

package com.davils.kontinuity.testing

import kotlinx.coroutines.delay
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration

/**
 * A plan of faults for the calls of a test: every call waits, and some calls fail, by the rules [faults] declared.
 *
 * The calls are numbered from 1 in the order they start; the rules decide by that number, so a plan is deterministic
 * and safe to share between coroutines. Run a call through it with [inject] or the operator form
 * `faults { call() }`.
 *
 * @since 1.0.0
 */
public class Faults internal constructor(builder: FaultsBuilder) {
    private val latency: Duration = builder.latency
    private val failures: List<FailureRule> = builder.failures.toList()
    private val counter = AtomicInt(0)

    /**
     * How many calls started.
     *
     * @since 1.0.0
     */
    public val calls: Int get() = counter.load()

    /**
     * Runs [block] as the next call of the plan: waits the latency, then fails if a rule says so, else runs [block].
     *
     * @param T the type of the result.
     * @param block the call.
     * @return the result of [block].
     * @throws Throwable the failure of a rule, or what [block] throws.
     * @since 1.0.0
     */
    public suspend fun <T> inject(block: suspend () -> T): T {
        val number = counter.addAndFetch(1)
        if (latency.isPositive()) delay(latency)
        val failure = failures.firstNotNullOfOrNull { rule -> rule.failureFor(number) }
        if (failure != null) throw failure
        return block()
    }

    /**
     * Runs [block] as the next call of the plan, the operator form of [inject].
     *
     * @param T the type of the result.
     * @param block the call.
     * @return the result of [block].
     * @throws Throwable the failure of a rule, or what [block] throws.
     * @since 1.0.0
     */
    public suspend operator fun <T> invoke(block: suspend () -> T): T = inject(block)

    /**
     * Shows the latency, the number of rules and the calls so far.
     *
     * @return the plan as text.
     * @since 1.0.0
     */
    override fun toString(): String = "Faults(latency=$latency, rules=${failures.size}, calls=$calls)"
}
