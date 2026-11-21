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
 * The window of a [CircuitBreaker] at one moment: the closed or metrics-only window, the trial calls of a half-open
 * breaker, or the window an open breaker opened with; empty for a disabled or forced-open breaker.
 *
 * @property failureRate the failure rate in percent, or -1 while the window holds fewer calls than needed.
 * @property slowCallRate the rate of slow calls in percent, or -1 while the window holds fewer calls than needed.
 * @property bufferedCalls the calls in the window.
 * @property failedCalls the failed calls in the window.
 * @property slowCalls the slow calls in the window.
 * @property slowFailedCalls the calls in the window that were slow and failed.
 * @since 1.0.0
 */
public class WindowSnapshot internal constructor(
    public val failureRate: Double,
    public val slowCallRate: Double,
    public val bufferedCalls: Int,
    public val failedCalls: Int,
    public val slowCalls: Int,
    public val slowFailedCalls: Int,
) {
    /**
     * Shows every value.
     *
     * @return the window as text.
     * @since 1.0.0
     */
    override fun toString(): String = "WindowSnapshot(failureRate=$failureRate, slowCallRate=$slowCallRate, " +
        "bufferedCalls=$bufferedCalls, failedCalls=$failedCalls, slowCalls=$slowCalls, " +
        "slowFailedCalls=$slowFailedCalls)"
}
