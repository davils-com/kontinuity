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

package com.davils.kontinuity.policy

/**
 * How many calls a [Policy] counted since it was created; every call is counted exactly once and the totals never
 * fall.
 *
 * @property successful the calls that returned a result.
 * @property failed the calls that failed.
 * @property rejected the calls a stage of the policy refused.
 * @property cancelled the calls whose caller was cancelled.
 * @since 1.0.0
 */
public class PolicySnapshot internal constructor(
    public val successful: Long,
    public val failed: Long,
    public val rejected: Long,
    public val cancelled: Long,
) {
    /**
     * The calls that ended: the sum of every total.
     *
     * @since 1.0.0
     */
    public val calls: Long get() = successful + failed + rejected + cancelled

    /**
     * Shows every total.
     *
     * @return the snapshot as text.
     * @since 1.0.0
     */
    override fun toString(): String =
        "PolicySnapshot(successful=$successful, failed=$failed, rejected=$rejected, cancelled=$cancelled)"
}
