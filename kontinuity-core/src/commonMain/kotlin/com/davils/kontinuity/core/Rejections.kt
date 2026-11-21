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

private const val MAXIMUM_CAUSES: Int = 32

/**
 * Returns the first [CallRejectedException] in the cause chain of this exception, itself included.
 *
 * The walk stops after 32 causes and at the first cause it has seen before, so a long or cyclic chain built by an
 * attacker costs no more than that. Suppressed exceptions are not searched.
 *
 * @receiver the failure to search.
 * @return the rejection, or null if the chain holds none within 32 causes.
 * @since 1.0.0
 */
public fun Throwable.rejectionOrNull(): CallRejectedException? = causeChain().firstNotNullOfOrNull { cause ->
    cause as? CallRejectedException
}

/**
 * Returns this exception and its causes, at most 32 of them, stopping at the first cause seen before.
 *
 * @receiver the failure whose chain is walked.
 * @return the chain, starting with the receiver.
 * @since 1.0.0
 */
public fun Throwable.causeChain(): List<Throwable> {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = this
    while (current != null && chain.size < MAXIMUM_CAUSES && chain.none { seen -> seen === current }) {
        chain += current
        current = current.cause
    }
    return chain
}
