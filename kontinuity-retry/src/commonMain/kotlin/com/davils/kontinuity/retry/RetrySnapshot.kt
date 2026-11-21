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

package com.davils.kontinuity.retry

/**
 * How many calls a [Retry] counted since it was created, as the metrics of a retry of resilience4j; the counts never
 * fall and taking a snapshot changes nothing.
 *
 * Every call that ended is counted exactly once, so `successfulWithoutRetry + successfulWithRetry +
 * failedWithoutRetry + failedWithRetry + cancelled` is the number of calls that ended. A call counts as "with retry"
 * when it ran more than one attempt.
 *
 * @property successfulWithoutRetry the calls that succeeded on their first attempt.
 * @property successfulWithRetry the calls that succeeded after at least one retry.
 * @property failedWithoutRetry the calls that failed on their first attempt, such as with a failure the retry does
 *   not repeat.
 * @property failedWithRetry the calls that failed after at least one retry.
 * @property cancelled the calls whose caller was cancelled or interrupted.
 * @property retries the retries of every call, the first attempts not included.
 * @since 1.0.0
 */
public class RetrySnapshot internal constructor(
    public val successfulWithoutRetry: Long,
    public val successfulWithRetry: Long,
    public val failedWithoutRetry: Long,
    public val failedWithRetry: Long,
    public val cancelled: Long,
    public val retries: Long,
) {
    /**
     * The calls that ended, in any way.
     *
     * @since 1.0.0
     */
    public val calls: Long
        get() = successfulWithoutRetry + successfulWithRetry + failedWithoutRetry + failedWithRetry + cancelled

    /**
     * Shows every count.
     *
     * @return the snapshot as text.
     * @since 1.0.0
     */
    override fun toString(): String = "RetrySnapshot(successfulWithoutRetry=$successfulWithoutRetry, " +
        "successfulWithRetry=$successfulWithRetry, failedWithoutRetry=$failedWithoutRetry, " +
        "failedWithRetry=$failedWithRetry, cancelled=$cancelled, retries=$retries)"
}
