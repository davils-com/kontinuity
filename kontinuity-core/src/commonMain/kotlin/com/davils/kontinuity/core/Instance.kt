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

package com.davils.kontinuity.core

/**
 * An instance of kontinuity: a circuit breaker, a retry, a cache or any other component with a name.
 *
 * Every implementation of kontinuity is safe to use from many coroutines and threads at once.
 *
 * @since 1.0.0
 */
public interface Instance {
    /**
     * The name of the instance, unique within its registry.
     *
     * @since 1.0.0
     */
    public val name: InstanceName

    /**
     * What the instance is.
     *
     * @since 1.0.0
     */
    public val kind: ResilienceKind
}
