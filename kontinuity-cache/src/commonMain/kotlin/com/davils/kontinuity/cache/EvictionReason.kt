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

package com.davils.kontinuity.cache

/**
 * Why a [Cache] evicted entries.
 *
 * @property id the name of the reason in events and meters.
 * @since 1.0.0
 */
public enum class EvictionReason(public val id: String) {
    /**
     * The cache held more entries than [CacheConfig.maximumSize] allows.
     *
     * @since 1.0.0
     */
    Size("size"),

    /**
     * The entries weighed more than [CacheConfig.maximumWeight] allows, or one entry alone weighed more.
     *
     * @since 1.0.0
     */
    Weight("weight"),
}
