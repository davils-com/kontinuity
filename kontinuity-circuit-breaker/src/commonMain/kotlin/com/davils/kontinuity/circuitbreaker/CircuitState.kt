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

/**
 * The state of a [CircuitBreaker], the states of resilience4j.
 *
 * @property isPermitting whether the breaker lets calls through in this state; a half-open breaker lets only its
 *   trial calls through.
 * @since 1.0.0
 */
public enum class CircuitState(public val isPermitting: Boolean) {
    /**
     * Calls pass and their outcomes fill the sliding window; the breaker opens when a rate crosses its threshold.
     *
     * @since 1.0.0
     */
    Closed(isPermitting = true),

    /**
     * Calls are refused until the wait of the open state is over.
     *
     * @since 1.0.0
     */
    Open(isPermitting = false),

    /**
     * A limited number of trial calls pass; their outcomes decide whether the breaker closes or opens again.
     *
     * @since 1.0.0
     */
    HalfOpen(isPermitting = true),

    /**
     * Calls pass and nothing is recorded, until the breaker is moved to another state by hand.
     *
     * @since 1.0.0
     */
    Disabled(isPermitting = true),

    /**
     * Calls are refused, until the breaker is moved to another state by hand.
     *
     * @since 1.0.0
     */
    ForcedOpen(isPermitting = false),

    /**
     * Calls pass and their outcomes are recorded and reported, but the breaker never opens.
     *
     * @since 1.0.0
     */
    MetricsOnly(isPermitting = true),
}
