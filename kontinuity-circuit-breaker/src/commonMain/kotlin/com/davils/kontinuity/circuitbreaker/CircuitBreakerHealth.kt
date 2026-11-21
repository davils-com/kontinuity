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

import com.davils.kollect.core.health.Health
import com.davils.kollect.core.health.HealthIndicator
import com.davils.kollect.core.health.ReasonCode

/**
 * Returns a health indicator of this breaker for a kollect health registry: Up while it is closed, disabled or
 * metrics-only, Degraded while it is half-open and Down while it is open or forced open.
 *
 * The indicator only reads the state, so checking health never changes the breaker.
 *
 * @receiver the breaker.
 * @return the indicator.
 * @since 1.0.0
 */
public fun CircuitBreaker.healthIndicator(): HealthIndicator = HealthIndicator { healthOf(state) }

/**
 * Returns a health indicator of every breaker of this registry: the worst health of its breakers when it is checked,
 * Up for an empty registry.
 *
 * @receiver the registry.
 * @return the indicator.
 * @since 1.0.0
 */
public fun CircuitBreakerRegistry.healthIndicator(): HealthIndicator = HealthIndicator {
    val states = all.map { breaker -> breaker.state }
    states.map(::healthOf).maxByOrNull { health -> health.status.ordinal } ?: Health.Up
}

private fun healthOf(state: CircuitState): Health = when (state) {
    CircuitState.Closed, CircuitState.Disabled, CircuitState.MetricsOnly -> Health.Up
    CircuitState.HalfOpen -> Health.degraded(ReasonCode.StateMatched)
    CircuitState.Open, CircuitState.ForcedOpen -> Health.down(ReasonCode.StateMatched)
}
