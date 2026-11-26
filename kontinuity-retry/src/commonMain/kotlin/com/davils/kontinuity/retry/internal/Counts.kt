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

@file:OptIn(DavilsInternal::class)

package com.davils.kontinuity.retry.internal

import com.davils.kontinuity.retry.RetrySnapshot
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.concurrency.Guard
import com.davils.kore.core.concurrency.guard

internal class Counts {
    private val guard: Guard = guard()
    private val calls = LongArray(CallKind.entries.size)
    private var retries: Long = 0L

    fun count(kind: CallKind) {
        guard.locked { calls[kind.ordinal] += 1L }
    }

    fun retried() {
        guard.locked { retries += 1L }
    }

    fun snapshot(): RetrySnapshot = guard.locked {
        RetrySnapshot(
            successfulWithoutRetry = calls[CallKind.SuccessfulWithoutRetry.ordinal],
            successfulWithRetry = calls[CallKind.SuccessfulWithRetry.ordinal],
            failedWithoutRetry = calls[CallKind.FailedWithoutRetry.ordinal],
            failedWithRetry = calls[CallKind.FailedWithRetry.ordinal],
            cancelled = calls[CallKind.Cancelled.ordinal],
            retries = retries,
        )
    }
}
