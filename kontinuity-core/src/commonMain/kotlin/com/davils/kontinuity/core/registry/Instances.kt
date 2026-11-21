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

@file:OptIn(DavilsInternal::class)

package com.davils.kontinuity.core.registry

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.Instance
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.telemetry.Guard
import com.davils.kontinuity.core.telemetry.guard
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.collection.immutableList
import com.davils.kore.core.text.SafeText
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The implementation of [Registry] that every registry of kontinuity delegates to.
 *
 * @param C the type of the configurations.
 * @param I the type of the instances.
 * @param kind what the registry holds.
 * @param settings the limits and configurations of the registry.
 * @param environment the environment of the registry; its event capacity bounds [events].
 * @param create creates an instance; it runs while the registry is locked and must not call foreign code.
 * @since 1.0.0
 */
@DavilsInternal
public class Instances<C : Any, I : Instance>(
    private val kind: ResilienceKind,
    override val settings: RegistrySettings<C>,
    environment: Environment,
    private val create: (InstanceName, C) -> I,
) : Registry<C, I> {
    private val guard: Guard = guard()
    private val entries: MutableMap<String, I> = linkedMapOf()
    private val changes = MutableSharedFlow<RegistryEvent<I>>(
        extraBufferCapacity = environment.eventCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val all: List<I> get() = guard.locked { entries.values.toList() }.immutableList()

    override val events: Flow<RegistryEvent<I>> = changes.asSharedFlow()

    override fun find(name: String): I? = guard.locked { entries[name] }

    /**
     * Returns the instance named [name], created from the configuration named [config] if the registry has none.
     *
     * @param name the name of the instance.
     * @param config the name of the configuration.
     * @return the instance.
     * @throws IllegalArgumentException if [name] is no valid instance name or [config] names no configuration.
     * @throws InstanceLimitException if the instance is new and the registry is full.
     * @since 1.0.0
     */
    public fun getOrCreate(name: String, config: String): I {
        val chosen = requireNotNull(settings.config(config)) {
            "The ${kind.id} registry has no configuration ${SafeText.quoted(config)}"
        }
        return getOrCreate(name, chosen)
    }

    /**
     * Returns the instance named [name], created from [config] if the registry has none.
     *
     * @param name the name of the instance.
     * @param config the configuration of a new instance.
     * @return the instance.
     * @throws IllegalArgumentException if [name] is no valid instance name.
     * @throws InstanceLimitException if the instance is new and the registry is full.
     * @since 1.0.0
     */
    public fun getOrCreate(name: String, config: C): I {
        val instanceName = InstanceName.of(name)
        val lookup = guard.locked {
            val existing = entries[name]
            existing?.let { found -> Lookup(found, isNew = false) } ?: Lookup(added(instanceName, config), isNew = true)
        }
        if (lookup.isNew) changes.tryEmit(RegistryEvent.Added(lookup.instance))
        return lookup.instance
    }

    override fun remove(name: String): I? {
        val removed = guard.locked { entries.remove(name) } ?: return null
        changes.tryEmit(RegistryEvent.Removed(removed))
        return removed
    }

    override fun replace(instance: I): I? {
        val name = instance.name.value
        val previous = guard.locked {
            val existing = entries[name]
            if (existing == null) checkRoom()
            entries[name] = instance
            existing
        }
        val change = previous?.let { replaced -> RegistryEvent.Replaced(replaced, instance) }
        changes.tryEmit(change ?: RegistryEvent.Added(instance))
        return previous
    }

    /**
     * Shows the kind and the number of instances.
     *
     * @return the registry as text.
     * @since 1.0.0
     */
    override fun toString(): String = "Registry(${kind.id}, instances=${guard.locked { entries.size }})"

    private fun added(name: InstanceName, config: C): I {
        checkRoom()
        val instance = create(name, config)
        entries[name.value] = instance
        return instance
    }

    private fun checkRoom() {
        val maximum = settings.limits.maximumInstances
        if (entries.size >= maximum) throw InstanceLimitException(kind, maximum)
    }

    private data class Lookup<I>(val instance: I, val isNew: Boolean)
}
