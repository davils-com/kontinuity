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

import com.davils.kontinuity.core.Instance
import kotlinx.coroutines.flow.Flow

/**
 * The named instances of one kind and the configurations they are created from, the way the registries of
 * resilience4j hold them.
 *
 * Every registry is safe to use from many coroutines and threads at once. It creates an instance at most once per
 * name; a later request for the same name returns the existing instance, whatever configuration it names. It holds at
 * most [InstanceLimits.maximumInstances] instances. A registry is no global: an application creates the registries it
 * needs and hands them where they are needed.
 *
 * @param C the type of the configurations.
 * @param I the type of the instances.
 * @since 1.0.0
 */
public interface Registry<C : Any, I : Instance> {
    /**
     * The limits, the default configuration and the named configurations of the registry.
     *
     * @since 1.0.0
     */
    public val settings: RegistrySettings<C>

    /**
     * The instances of the registry, in the order they were added.
     *
     * @since 1.0.0
     */
    public val all: List<I>

    /**
     * The changes of the instances, hot and without replay; a slow collector loses the oldest changes.
     *
     * @since 1.0.0
     */
    public val events: Flow<RegistryEvent<I>>

    /**
     * Returns the instance named [name], or null if the registry has none.
     *
     * @param name the name of the instance; a name that is no valid instance name finds nothing.
     * @return the instance, or null.
     * @since 1.0.0
     */
    public fun find(name: String): I?

    /**
     * Removes the instance named [name].
     *
     * @param name the name of the instance.
     * @return the removed instance, or null if the registry had none.
     * @since 1.0.0
     */
    public fun remove(name: String): I?

    /**
     * Puts [instance] under its name, in place of an instance of the same name if there is one.
     *
     * @param instance the instance, which another registry may hold, too.
     * @return the replaced instance, or null if there was none.
     * @throws InstanceLimitException if the name is new and the registry is full.
     * @since 1.0.0
     */
    public fun replace(instance: I): I?
}
