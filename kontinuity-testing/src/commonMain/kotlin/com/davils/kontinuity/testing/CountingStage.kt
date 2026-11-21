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

import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.Stage
import com.davils.kore.core.failure.attempted
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A stage that only counts the calls through it, to check how often an outer stage calls an inner one.
 *
 * @property name the name of the stage; `counting` by default.
 * @since 1.0.0
 */
public class CountingStage(override val name: InstanceName = InstanceName.of("counting")) : Stage {
    private val started = AtomicInt(0)
    private val succeeded = AtomicInt(0)
    private val failed = AtomicInt(0)

    override val kind: ResilienceKind get() = ResilienceKind.Policy

    /**
     * How many calls started.
     *
     * @since 1.0.0
     */
    public val calls: Int get() = started.load()

    /**
     * How many calls returned a result.
     *
     * @since 1.0.0
     */
    public val successes: Int get() = succeeded.load()

    /**
     * How many calls threw.
     *
     * @since 1.0.0
     */
    public val failures: Int get() = failed.load()

    /**
     * Counts the call and runs [block]; a cancellation of the caller is passed on and not counted.
     *
     * @param T the type of the result.
     * @param block the call.
     * @return the result of [block].
     * @throws Throwable what [block] throws.
     * @since 1.0.0
     */
    override suspend fun <T> execute(block: suspend () -> T): T {
        started.addAndFetch(1)
        val result = attempted { block() }
        result.onFailure { failed.addAndFetch(1) }
        result.onSuccess { succeeded.addAndFetch(1) }
        return result.getOrThrow()
    }

    /**
     * Shows the counts.
     *
     * @return the stage as text.
     * @since 1.0.0
     */
    override fun toString(): String = "CountingStage($name, calls=$calls, successes=$successes, failures=$failures)"
}
