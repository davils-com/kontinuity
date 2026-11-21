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

import com.davils.kontinuity.core.InstanceName
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.collection.immutableList
import com.davils.kore.core.collection.immutableMap
import com.davils.kore.core.validation.Validatable
import com.davils.kore.core.validation.ValidationScope
import com.davils.kore.core.validation.atMost

private const val UNKNOWN_BASE: String = "has a configuration whose base is not declared before it"

/**
 * The limits, the default configuration and the named configurations of a registry, as its builder declared them.
 *
 * @param C the type of the configurations.
 * @property limits the limits of the registry.
 * @property defaults the default configuration, the configuration named [DEFAULT_CONFIG].
 * @param configs the named configurations.
 * @param unknownBases the bases that named no configuration declared before them.
 * @since 1.0.0
 */
@OptIn(DavilsInternal::class)
public class RegistrySettings<C : Any> internal constructor(
    public val limits: InstanceLimits,
    public val defaults: C,
    configs: Map<String, C>,
    private val unknownBases: List<String>,
) : Validatable {
    /**
     * The named configurations by name, in no particular order; [configNames] lists them in the order they were
     * declared.
     *
     * @since 1.0.0
     */
    public val configs: Map<String, C> = configs.immutableMap()

    /**
     * The names of the configurations, in the order they were declared.
     *
     * @since 1.0.0
     */
    public val configNames: List<String> = configs.keys.toList().immutableList()

    /**
     * Returns the configuration named [name]: [defaults] for [DEFAULT_CONFIG], a named configuration otherwise.
     *
     * @param name the name of the configuration.
     * @return the configuration, or null if there is none of that name.
     * @since 1.0.0
     */
    public fun config(name: String): C? {
        if (name == DEFAULT_CONFIG) return defaults
        return configs[name]
    }

    /**
     * Checks the number and the names of the configurations and that every base names an earlier configuration.
     *
     * @receiver the validation that collects the violations.
     * @since 1.0.0
     */
    override fun ValidationScope.validate() {
        atMost(property = ::configs, value = configs.size, maximum = limits.maximumConfigs)
        val invalid = configs.keys.count { name -> !InstanceName.isValid(name) || name == DEFAULT_CONFIG }
        if (invalid > 0) fail(property = ::configs, message = "has $invalid reserved names or no instance names")
        if (unknownBases.isNotEmpty()) fail(property = ::configs, message = UNKNOWN_BASE)
    }

    /**
     * Shows the limits and the names of the configurations.
     *
     * @return the settings as text.
     * @since 1.0.0
     */
    override fun toString(): String = "RegistrySettings($limits, configs=${configs.size})"

    /**
     * The name of the default configuration.
     *
     * @since 1.0.0
     */
    public companion object {
        /**
         * The name of the default configuration: `default`.
         *
         * @since 1.0.0
         */
        public const val DEFAULT_CONFIG: String = "default"
    }
}
