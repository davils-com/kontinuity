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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The events a flow emitted while the log listened, in order, such as the events of a circuit breaker.
 *
 * Create one with [recordEvents], which subscribes at once, so no event emitted after it returns is missed. Stop it
 * with [stop] or with the end of its scope.
 *
 * @param E the type of the events.
 * @param scope the scope the log listens in.
 * @param flow the events.
 * @since 1.0.0
 */
public class EventLog<E> internal constructor(scope: CoroutineScope, flow: Flow<E>) {
    private val recorded: MutableList<E> = mutableListOf()
    private val job: Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        flow.collect { event -> recorded += event }
    }

    /**
     * The events so far.
     *
     * @since 1.0.0
     */
    public val events: List<E> get() = recorded.toList()

    /**
     * Stops listening.
     *
     * @since 1.0.0
     */
    public fun stop() {
        job.cancel()
    }

    /**
     * Returns the events of type [T] so far.
     *
     * @param T the type of event.
     * @return the events of that type.
     * @since 1.0.0
     */
    public inline fun <reified T : E> only(): List<T> = events.filterIsInstance<T>()

    /**
     * Shows the number of events.
     *
     * @return the log as text.
     * @since 1.0.0
     */
    override fun toString(): String = "EventLog(${recorded.size} events)"
}
