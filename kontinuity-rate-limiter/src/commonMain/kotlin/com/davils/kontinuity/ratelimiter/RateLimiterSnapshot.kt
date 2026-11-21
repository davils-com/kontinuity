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

package com.davils.kontinuity.ratelimiter

import kotlin.time.Duration

/**
 * What a [RateLimiter] knows at one moment; taking a snapshot changes nothing.
 *
 * Every request for permits that ended is counted exactly once, so `acquired + rejected + cancelled` is the number of
 * requests that ended; a request refused because its arguments are invalid counts as none. While calls run, the
 * values are read one after the other, not as one atomic cut.
 *
 * @property availablePermissions the permits a caller could take at once now.
 * @property waitingCallers the callers that wait for permits now.
 * @property limitForPeriod the limit per period now.
 * @property timeout the timeout of callers now.
 * @property acquired the requests that got their permits.
 * @property rejected the requests the limiter refused.
 * @property cancelled the requests cancelled or interrupted while they waited.
 * @property permitsGranted the permits of all acquired requests.
 * @since 1.0.0
 */
public class RateLimiterSnapshot internal constructor(
    public val availablePermissions: Int,
    public val waitingCallers: Int,
    public val limitForPeriod: Int,
    public val timeout: Duration,
    public val acquired: Long,
    public val rejected: Long,
    public val cancelled: Long,
    public val permitsGranted: Long,
) {
    /**
     * Shows every value.
     *
     * @return the snapshot as text.
     * @since 1.0.0
     */
    override fun toString(): String = "RateLimiterSnapshot(availablePermissions=$availablePermissions, " +
        "waitingCallers=$waitingCallers, limitForPeriod=$limitForPeriod, timeout=$timeout, acquired=$acquired, " +
        "rejected=$rejected, cancelled=$cancelled, permitsGranted=$permitsGranted)"
}
