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

import com.davils.kontinuity.core.InstanceName

/**
 * Builds a validated fallback: `fallback<User>("user") { on<IOException> { cachedUser() } }`.
 *
 * @param T the type of the result.
 * @param name the name of the fallback, as [InstanceName] checks it.
 * @param configure declares the handlers in the order they are tried.
 * @return the fallback.
 * @throws IllegalArgumentException if [name] is not a valid instance name.
 * @throws com.davils.kore.core.validation.report.ValidationException if the fallback has no handler or more than
 *   [Fallback.HARD_HANDLERS].
 * @since 1.0.0
 */
public fun <T> fallback(name: String, configure: FallbackBuilder<T>.() -> Unit): Fallback<T> {
    val builder = FallbackBuilder<T>(InstanceName.of(name))
    builder.configure()
    return builder.build()
}
