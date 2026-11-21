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

package com.davils.kontinuity.bulkhead

/**
 * What a [DispatcherBulkhead] knows at one moment: its running and queued calls and its totals. Taking a snapshot
 * changes nothing.
 *
 * Every call is counted exactly once, as [permitted], [rejected] or [cancelled], so
 * `permitted + rejected + cancelled + queued` is the number of calls the bulkhead was asked to run, and every
 * permitted call ends once, so `permitted - finished` is [running].
 *
 * @property running how many calls run right now; a running call that suspends frees its thread of the dispatcher
 *   but still counts as running.
 * @property queued how many admitted calls wait for their turn on the dispatcher.
 * @property remainingCapacity how many more calls the bulkhead would admit right now.
 * @property permitted how many calls started on the dispatcher since the bulkhead was created.
 * @property rejected how many calls the bulkhead refused because its capacity was full.
 * @property finished how many calls that started have ended.
 * @property cancelled how many callers were cancelled before their call started.
 * @since 1.0.0
 */
public class DispatcherBulkheadSnapshot internal constructor(
    public val running: Int,
    public val queued: Int,
    public val remainingCapacity: Int,
    public val permitted: Long,
    public val rejected: Long,
    public val finished: Long,
    public val cancelled: Long,
) {
    /**
     * Shows every value.
     *
     * @return the snapshot as text.
     * @since 1.0.0
     */
    override fun toString(): String = "DispatcherBulkheadSnapshot(running=$running, queued=$queued, " +
        "remainingCapacity=$remainingCapacity, permitted=$permitted, rejected=$rejected, finished=$finished, " +
        "cancelled=$cancelled)"
}
