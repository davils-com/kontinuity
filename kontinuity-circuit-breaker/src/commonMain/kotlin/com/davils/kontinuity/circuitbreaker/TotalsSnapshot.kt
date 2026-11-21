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
 * How many calls a [CircuitBreaker] counted since it was created; the totals never fall.
 *
 * Every permitted call that ended is counted exactly once, so `successful + failed + ignored + released` is the
 * number of permitted calls that ended.
 *
 * @property successful the calls that succeeded.
 * @property failed the calls that failed.
 * @property ignored the calls that ended with an ignored failure.
 * @property released the permitted calls that ended without an outcome, such as by a cancellation.
 * @property notPermitted the calls the breaker refused.
 * @since 1.0.0
 */
public class TotalsSnapshot internal constructor(
    public val successful: Long,
    public val failed: Long,
    public val ignored: Long,
    public val released: Long,
    public val notPermitted: Long,
) {
    /**
     * Shows every total.
     *
     * @return the totals as text.
     * @since 1.0.0
     */
    override fun toString(): String = "TotalsSnapshot(successful=$successful, failed=$failed, ignored=$ignored, " +
        "released=$released, notPermitted=$notPermitted)"
}
