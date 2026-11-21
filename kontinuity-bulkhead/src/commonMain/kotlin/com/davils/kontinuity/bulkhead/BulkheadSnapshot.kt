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
 * What a [Bulkhead] knows at one moment: its permits, its queue and its totals. Taking a snapshot changes nothing.
 *
 * Every request for a permit is counted exactly once, as [permitted], [rejected] or [cancelled], so
 * `permitted + rejected + cancelled` is the number of requests that were decided, and every permitted call gives its
 * permit back once, so `permitted - finished` is the number of permits held by callers right now.
 *
 * @property availableConcurrentCalls how many more calls the bulkhead would permit right now; never below zero, even
 *   while a smaller limit lets running calls finish.
 * @property maxAllowedConcurrentCalls the limit of concurrent calls of the current configuration.
 * @property concurrentCalls how many permits are taken right now.
 * @property waitingCalls how many calls wait for a permit right now.
 * @property permitted how many calls received a permit since the bulkhead was created.
 * @property rejected how many calls the bulkhead refused, because it was full, its queue was full or a wait ran out.
 * @property finished how many permits were given back.
 * @property cancelled how many callers were cancelled while they waited for a permit.
 * @since 1.0.0
 */
public class BulkheadSnapshot internal constructor(
    public val availableConcurrentCalls: Int,
    public val maxAllowedConcurrentCalls: Int,
    public val concurrentCalls: Int,
    public val waitingCalls: Int,
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
    override fun toString(): String = "BulkheadSnapshot(available=$availableConcurrentCalls, " +
        "maxAllowed=$maxAllowedConcurrentCalls, concurrent=$concurrentCalls, waiting=$waitingCalls, " +
        "permitted=$permitted, rejected=$rejected, finished=$finished, cancelled=$cancelled)"
}
