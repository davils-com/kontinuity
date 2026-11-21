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

package com.davils.kontinuity.hedge

import kotlin.time.Duration

/**
 * What a [Hedge] counted since it was created and the delay it waits now; the totals never fall and taking a
 * snapshot changes nothing.
 *
 * Every call that ended is counted exactly once, so `primarySucceeded + hedgeSucceeded + failed + cancelled` is the
 * number of calls that ended. [hedgesStarted] counts the hedged copies, not the calls.
 *
 * @property primarySucceeded the calls whose primary copy succeeded first.
 * @property hedgeSucceeded the calls whose hedged copy succeeded first.
 * @property failed the calls that ended with a failure.
 * @property cancelled the calls whose caller was cancelled.
 * @property hedgesStarted the hedged copies started, at most [HedgeConfig.maxHedgedAttempts] per call.
 * @property delay the delay the next call waits before its first hedged copy.
 * @since 1.0.0
 */
public class HedgeSnapshot internal constructor(
    public val primarySucceeded: Long,
    public val hedgeSucceeded: Long,
    public val failed: Long,
    public val cancelled: Long,
    public val hedgesStarted: Long,
    public val delay: Duration,
) {
    /**
     * The calls that ended, each counted once.
     *
     * @since 1.0.0
     */
    public val calls: Long get() = primarySucceeded + hedgeSucceeded + failed + cancelled

    /**
     * Shows every total and the delay.
     *
     * @return the snapshot as text.
     * @since 1.0.0
     */
    override fun toString(): String = "HedgeSnapshot(primarySucceeded=$primarySucceeded, " +
        "hedgeSucceeded=$hedgeSucceeded, failed=$failed, cancelled=$cancelled, hedgesStarted=$hedgesStarted, " +
        "delay=$delay)"
}
