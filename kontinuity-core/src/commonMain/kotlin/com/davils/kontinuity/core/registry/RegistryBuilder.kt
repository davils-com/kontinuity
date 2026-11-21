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

import com.davils.kore.core.annotation.DavilsDsl
import com.davils.kore.core.dsl.ValidatingBuilder
import com.davils.kore.core.validation.Validatable

/**
 * Builds the [RegistrySettings] of a registry: its limits, its default configuration and its named configurations.
 *
 * `defaults { }` changes the default configuration, which starts from the built-in defaults of the component. Every
 * `config(name, base) { }` starts from the configuration named `base`, the default configuration unless set, and
 * changes what its block sets, so configurations inherit from each other the way the base configurations of
 * resilience4j do. A base must be declared before the configuration that names it; the blocks run in the order
 * they are declared, after `limits { }` and `defaults { }`, wherever those stand. Every problem of every block is
 * reported at once by `build()`.
 *
 * @param C the type of the configurations.
 * @param B the type of the builder of a configuration.
 * @param builtIn the built-in default configuration of the component.
 * @since 1.0.0
 */
@DavilsDsl
public abstract class RegistryBuilder<C : Validatable, B : ValidatingBuilder<C>> protected constructor(
    private val builtIn: C,
) : ValidatingBuilder<RegistrySettings<C>>() {
    private val limitBlocks: MutableList<InstanceLimitsBuilder.() -> Unit> = mutableListOf()
    private val defaultBlocks: MutableList<B.() -> Unit> = mutableListOf()
    private val configBlocks: MutableList<ConfigBlock<B>> = mutableListOf()

    override val subject: String get() = "registry"

    /**
     * Returns a builder of a configuration that starts from [base].
     *
     * @param base the configuration the builder starts from.
     * @return the builder.
     * @since 1.0.0
     */
    protected abstract fun configBuilder(base: C): B

    /**
     * Sets the limits of the registry; see [InstanceLimits].
     *
     * @param configure sets the limits that differ from [InstanceLimits.Default].
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun limits(configure: InstanceLimitsBuilder.() -> Unit) {
        checkOpen()
        limitBlocks += configure
    }

    /**
     * Changes the default configuration, the configuration named [RegistrySettings.DEFAULT_CONFIG].
     *
     * @param configure changes what differs from the built-in defaults.
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun defaults(configure: B.() -> Unit) {
        checkOpen()
        defaultBlocks += configure
    }

    /**
     * Declares the configuration [name], which starts from the configuration [base].
     *
     * @param name the name of the configuration, as an instance name; it must not be
     *   [RegistrySettings.DEFAULT_CONFIG].
     * @param base the configuration it starts from, declared before it; the default configuration by default.
     * @param configure changes what differs from [base].
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public fun config(name: String, base: String = RegistrySettings.DEFAULT_CONFIG, configure: B.() -> Unit) {
        checkOpen()
        configBlocks += ConfigBlock(name, base, configure)
    }

    final override fun assemble(): RegistrySettings<C> {
        var limits = InstanceLimits.Default
        limitBlocks.forEach { block ->
            nested(InstanceLimitsBuilder(), RegistrySettings<*>::limits, block)?.let { declared -> limits = declared }
        }
        var defaults = builtIn
        defaultBlocks.forEach { block ->
            val declared = nested(configBuilder(builtIn), RegistrySettings<*>::defaults, block)
            if (declared != null) defaults = declared
        }
        val configs = linkedMapOf<String, C>()
        val unknownBases = mutableListOf<String>()
        configBlocks.forEach { block ->
            val base = baseOf(block.base, defaults, configs)
            if (base == null) unknownBases += block.base
            collect(
                target = configs,
                key = block.name,
                builder = configBuilder(base ?: defaults),
                property = RegistrySettings<*>::configs,
                block = block.configure,
            )
        }
        return RegistrySettings(limits = limits, defaults = defaults, configs = configs, unknownBases = unknownBases)
    }

    private fun baseOf(name: String, defaults: C, configs: Map<String, C>): C? {
        if (name == RegistrySettings.DEFAULT_CONFIG) return defaults
        return configs[name]
    }

    private data class ConfigBlock<B>(val name: String, val base: String, val configure: B.() -> Unit)
}
