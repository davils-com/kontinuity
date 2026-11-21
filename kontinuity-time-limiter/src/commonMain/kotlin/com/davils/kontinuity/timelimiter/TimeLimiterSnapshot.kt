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

package com.davils.kontinuity.timelimiter

/**
 * What a [TimeLimiter] counted since it was created; taking a snapshot changes nothing.
 *
 * Every call is counted exactly once, so `successful + failed + timedOut + cancelled + rejected` is the number of
 * calls that ended. [detachedFailed] counts again calls already counted as timed out or cancelled, whose detached
 * work failed after their caller stopped waiting. The totals are read one after another, so a snapshot taken while
 * calls end may be a moment apart between two totals.
 *
 * @property successful the calls that returned within their timeout.
 * @property failed the calls that failed within their timeout, the timeout of a callee included.
 * @property timedOut the calls that ran out of time.
 * @property cancelled the calls whose caller was cancelled before they ended.
 * @property rejected the calls a soft limiter refused because its detached calls were at their limit.
 * @property detachedInFlight the calls of a soft limiter whose detached work runs right now, whether their caller
 *   still waits or not.
 * @property detachedFailed the detached calls that failed after their caller stopped waiting.
 * @since 1.0.0
 */
public class TimeLimiterSnapshot internal constructor(
    public val successful: Long,
    public val failed: Long,
    public val timedOut: Long,
    public val cancelled: Long,
    public val rejected: Long,
    public val detachedInFlight: Int,
    public val detachedFailed: Long,
) {
    /**
     * Shows every total.
     *
     * @return the snapshot as text.
     * @since 1.0.0
     */
    override fun toString(): String = "TimeLimiterSnapshot(successful=$successful, failed=$failed, " +
        "timedOut=$timedOut, cancelled=$cancelled, rejected=$rejected, detachedInFlight=$detachedInFlight, " +
        "detachedFailed=$detachedFailed)"
}
