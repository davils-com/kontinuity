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

/**
 * A change of the instances of a registry, as its `events` flow reports it.
 *
 * @param I the type of the instances.
 * @since 1.0.0
 */
public sealed interface RegistryEvent<out I : Instance> {
    /**
     * The instance the change is about: the new one for [Added] and [Replaced], the removed one for [Removed].
     *
     * @since 1.0.0
     */
    public val instance: I

    /**
     * The registry created or took [instance].
     *
     * @param I the type of the instances.
     * @property instance the new instance.
     * @since 1.0.0
     */
    public class Added<out I : Instance> internal constructor(override val instance: I) : RegistryEvent<I> {
        /**
         * Shows the kind and the name of the instance.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Added(${instance.kind.id} ${instance.name})"
    }

    /**
     * The registry removed [instance].
     *
     * @param I the type of the instances.
     * @property instance the removed instance.
     * @since 1.0.0
     */
    public class Removed<out I : Instance> internal constructor(override val instance: I) : RegistryEvent<I> {
        /**
         * Shows the kind and the name of the instance.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Removed(${instance.kind.id} ${instance.name})"
    }

    /**
     * The registry replaced [previous] with [instance] under the same name.
     *
     * @param I the type of the instances.
     * @property previous the replaced instance.
     * @property instance the new instance.
     * @since 1.0.0
     */
    public class Replaced<out I : Instance> internal constructor(
        public val previous: I,
        override val instance: I,
    ) : RegistryEvent<I> {
        /**
         * Shows the kind and the name of the instance.
         *
         * @return the event as text.
         * @since 1.0.0
         */
        override fun toString(): String = "Replaced(${instance.kind.id} ${instance.name})"
    }
}
