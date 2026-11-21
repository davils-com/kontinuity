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

package com.davils.kontinuity.circuitbreaker

import com.davils.kontinuity.core.CallRejectedException
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind

/**
 * A circuit breaker refused a call because it is open, forced open or out of trial calls.
 *
 * @param instance the name of the breaker.
 * @property state the state of the breaker when it refused the call.
 * @since 1.0.0
 */
public class CallNotPermittedException internal constructor(
    instance: InstanceName,
    public val state: CircuitState,
) : CallRejectedException(
    instance = instance,
    kind = ResilienceKind.CircuitBreaker,
    message = "The circuit breaker $instance is ${state.name} and permits no further calls",
)
