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

/**
 * Builds [InstanceLimits]; every property starts from [InstanceLimits.Default].
 *
 * @since 1.0.0
 */
@DavilsDsl
public class InstanceLimitsBuilder internal constructor() : ValidatingBuilder<InstanceLimits>() {
    override val subject: String get() = "instance limits"

    /**
     * See [InstanceLimits.maximumInstances].
     *
     * @since 1.0.0
     */
    public var maximumInstances: Int = InstanceLimits.DEFAULT_INSTANCES
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [InstanceLimits.maximumConfigs].
     *
     * @since 1.0.0
     */
    public var maximumConfigs: Int = InstanceLimits.DEFAULT_CONFIGS
        set(value) {
            checkOpen()
            field = value
        }

    override fun assemble(): InstanceLimits = InstanceLimits(this)
}
