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

package com.davils.kontinuity.core.registry

import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.inRange
import com.davils.kore.core.validation.validateOrThrow

private const val SUBJECT: String = "instance limits"

/**
 * The limits of a registry: how many instances and how many named configurations it holds.
 *
 * Both limits are at least 1 and at most their hard ceiling, so a registry fed with names from requests cannot grow
 * without bound.
 *
 * @since 1.0.0
 */
public class InstanceLimits internal constructor(builder: InstanceLimitsBuilder) : Validatable {
    /**
     * The most instances of a registry: 1,024 by default, at most [HARD_INSTANCES].
     *
     * @since 1.0.0
     */
    public val maximumInstances: Int = builder.maximumInstances

    /**
     * The most named configurations of a registry, its default configuration not counted: 64 by default, at most
     * [HARD_CONFIGS].
     *
     * @since 1.0.0
     */
    public val maximumConfigs: Int = builder.maximumConfigs

    init {
        validateOrThrow(SUBJECT)
    }

    /**
     * Checks that both limits are within their bounds.
     *
     * @receiver the validation that collects the violations.
     * @since 1.0.0
     */
    override fun ValidationScope.validate() {
        inRange(property = ::maximumInstances, value = maximumInstances, range = 1..HARD_INSTANCES)
        inRange(property = ::maximumConfigs, value = maximumConfigs, range = 1..HARD_CONFIGS)
    }

    /**
     * Tells whether [other] is limits with the same values.
     *
     * @param other the object to compare with.
     * @return true if [other] is equal limits.
     * @since 1.0.0
     */
    override fun equals(other: Any?): Boolean =
        other is InstanceLimits && other.maximumInstances == maximumInstances && other.maximumConfigs == maximumConfigs

    /**
     * Returns a hash of both limits.
     *
     * @return the hash.
     * @since 1.0.0
     */
    override fun hashCode(): Int = HASH_FACTOR * maximumInstances + maximumConfigs

    /**
     * Shows both limits.
     *
     * @return the limits as text.
     * @since 1.0.0
     */
    override fun toString(): String =
        "InstanceLimits(maximumInstances=$maximumInstances, maximumConfigs=$maximumConfigs)"

    /**
     * The hard ceilings and the default limits.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The hard ceiling of [maximumInstances]: 65,536.
         *
         * @since 1.0.0
         */
        public const val HARD_INSTANCES: Int = 65_536

        /**
         * The hard ceiling of [maximumConfigs]: 1,024.
         *
         * @since 1.0.0
         */
        public const val HARD_CONFIGS: Int = 1_024

        internal const val DEFAULT_INSTANCES: Int = 1_024
        internal const val DEFAULT_CONFIGS: Int = 64
        private const val HASH_FACTOR: Int = 31

        /**
         * The limits that apply when none are set.
         *
         * @since 1.0.0
         */
        public val Default: InstanceLimits = InstanceLimits(InstanceLimitsBuilder())
    }
}
