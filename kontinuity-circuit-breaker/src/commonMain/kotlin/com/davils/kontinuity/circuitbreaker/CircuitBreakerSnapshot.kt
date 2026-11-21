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
 * What a [CircuitBreaker] knows at one moment: its state, its window and its totals. Taking a snapshot changes
 * nothing.
 *
 * @property state the state.
 * @property window the current window.
 * @property totals the calls counted since the breaker was created.
 * @since 1.0.0
 */
public class CircuitBreakerSnapshot internal constructor(
    public val state: CircuitState,
    public val window: WindowSnapshot,
    public val totals: TotalsSnapshot,
) {
    /**
     * Shows the state, the window and the totals.
     *
     * @return the snapshot as text.
     * @since 1.0.0
     */
    override fun toString(): String = "CircuitBreakerSnapshot($state, $window, $totals)"
}
