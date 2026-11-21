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

package com.davils.kontinuity.bulkhead

import com.davils.kontinuity.bulkhead.internal.Gate
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration

/**
 * The permission of a [Bulkhead] for one call, for callers that run the call themselves; [Bulkhead.execute] uses it,
 * too.
 *
 * A permit is given back exactly once: the first [release] frees its place for the next waiting call, every later
 * one is ignored, so a permit can be released in a `finally` block and from several threads at once without ever
 * freeing a place it does not hold. A permit that is never released keeps its place for good; release every permit.
 *
 * @since 1.0.0
 */
public class BulkheadPermit internal constructor(private val gate: Gate, private val since: ComparableTimeMark) {
    private val released = AtomicBoolean(false)

    /**
     * Whether the permit was given back.
     *
     * @since 1.0.0
     */
    public val isReleased: Boolean get() = released.load()

    /**
     * How long the permit has been held so far, measured with the time source of the environment.
     *
     * @since 1.0.0
     */
    public val held: Duration get() = since.elapsedNow()

    /**
     * Gives the permit back and hands its place to the longest waiting call, if any; idempotent and safe to call from
     * any thread.
     *
     * @since 1.0.0
     */
    public fun release() {
        if (released.compareAndSet(expectedValue = false, newValue = true)) gate.release(since)
    }

    /**
     * Shows whether the permit was given back.
     *
     * @return the permit as text.
     * @since 1.0.0
     */
    override fun toString(): String = "BulkheadPermit(${gate.name}, released=$isReleased)"
}
