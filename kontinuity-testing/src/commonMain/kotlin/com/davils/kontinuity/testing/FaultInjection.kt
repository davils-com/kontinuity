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

/**
 * Builds a plan of faults: `faults { latency = 100.milliseconds; failFirst(2) }`.
 *
 * @param configure declares the latency and the failures.
 * @return the plan.
 * @since 1.0.0
 */
public fun faults(configure: FaultsBuilder.() -> Unit): Faults {
    val builder = FaultsBuilder()
    builder.configure()
    return builder.build()
}

/**
 * Returns the failure every fault throws unless told otherwise: an [InjectedFailure] that names the call.
 *
 * @param number the number of the call.
 * @return the failure.
 * @since 1.0.0
 */
public fun injectedFailure(number: Int): Throwable = InjectedFailure(number)
