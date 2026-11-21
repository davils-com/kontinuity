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

import com.davils.kontinuity.cache.CacheConfig
import com.davils.kontinuity.core.InstanceName
import com.davils.kore.core.failure.contained
import kotlin.time.Duration

internal class Sizer<K : Any, V : Any>(private val name: InstanceName, private val config: CacheConfig<K, V>) {
    fun measured(key: K, value: V): Result<Sizing> {
        val weight = weightOf(key, value)
        val lifetime = minOf(a = config.expireAfterWrite, b = lifetimeOf(key, value))
        return weight.map { measuredWeight -> Sizing(measuredWeight, lifetime) }
    }

    private fun weightOf(key: K, value: V): Result<Int> {
        val weigher = config.weigher ?: return Result.success(1)
        val weighed = contained { weigher(key, value) }
        return weighed.fold(onSuccess = ::checked) { failure ->
            Result.failure(IllegalArgumentException("The weigher of the cache $name failed", failure))
        }
    }

    private fun checked(weight: Int): Result<Int> {
        if (weight < 0) return Result.failure(IllegalArgumentException("The weigher of the cache $name is negative"))
        return Result.success(weight)
    }

    private fun lifetimeOf(key: K, value: V): Duration {
        val lifetime = config.expireAfter ?: return Duration.INFINITE
        val computed = contained { lifetime(key, value) }.getOrElse { Duration.ZERO }
        return computed.takeIf { duration -> duration == Duration.INFINITE }
            ?: computed.coerceIn(minimumValue = Duration.ZERO, maximumValue = CacheConfig.HARD_DURATION)
    }
}
