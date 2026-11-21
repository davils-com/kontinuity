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

package com.davils.kontinuity.cache.internal

internal sealed interface Settled<out V : Any> {
    sealed interface Done<out V : Any> : Settled<V>

    class Loaded<out V : Any>(val value: V, val sizing: Sizing) : Done<V>

    class Failed(val cause: Throwable) : Done<Nothing>

    object Abandoned : Settled<Nothing>
}
