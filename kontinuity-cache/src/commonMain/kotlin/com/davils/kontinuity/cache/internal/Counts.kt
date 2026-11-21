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

internal class Counts {
    var hits: Long = 0L
    var misses: Long = 0L
    var loads: Long = 0L
    var loadFailures: Long = 0L
    var evictions: Long = 0L
    var expirations: Long = 0L
    var staleServed: Long = 0L

    fun lookedUp(isHit: Boolean) {
        if (isHit) hits += 1L
        if (!isHit) misses += 1L
    }

    fun settled(settled: Settled<*>) {
        if (settled is Settled.Loaded) loads += 1L
        if (settled is Settled.Failed) loadFailures += 1L
    }
}
