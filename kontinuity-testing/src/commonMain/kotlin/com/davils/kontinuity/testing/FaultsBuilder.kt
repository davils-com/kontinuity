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

package com.davils.kontinuity.testing

import com.davils.kore.core.annotation.DavilsDsl
import kotlin.time.Duration

/**
 * Declares the faults of a [Faults] plan; the first rule that matches a call decides its failure.
 *
 * @since 1.0.0
 */
@DavilsDsl
public class FaultsBuilder internal constructor() {
    internal val failures: MutableList<FailureRule> = mutableListOf()

    /**
     * How long every call waits before it runs or fails: zero by default.
     *
     * @since 1.0.0
     */
    public var latency: Duration = Duration.ZERO

    /**
     * Fails every call whose number is a multiple of [every], with the failure [failure] builds.
     *
     * @param every the period, at least 1; 1 fails every call.
     * @param failure builds the failure from the number of the call.
     * @throws IllegalArgumentException if [every] is below 1.
     * @since 1.0.0
     */
    public fun fail(every: Int = 1, failure: (Int) -> Throwable = ::injectedFailure) {
        require(every >= 1) { "A fault needs a period of at least 1" }
        failures += FailureRule(matches = { number -> number % every == 0 }, failure = failure)
    }

    /**
     * Fails the calls whose numbers are in [numbers], with the failure [failure] builds.
     *
     * @param numbers the numbers of the failing calls, counted from 1.
     * @param failure builds the failure from the number of the call.
     * @since 1.0.0
     */
    public fun failCalls(numbers: Collection<Int>, failure: (Int) -> Throwable = ::injectedFailure) {
        val chosen = numbers.toSet()
        failures += FailureRule(matches = { number -> number in chosen }, failure = failure)
    }

    /**
     * Fails the first [count] calls, with the failure [failure] builds, and lets the later calls run.
     *
     * @param count how many calls fail.
     * @param failure builds the failure from the number of the call.
     * @since 1.0.0
     */
    public fun failFirst(count: Int, failure: (Int) -> Throwable = ::injectedFailure) {
        failures += FailureRule(matches = { number -> number <= count }, failure = failure)
    }

    internal fun build(): Faults = Faults(this)
}
