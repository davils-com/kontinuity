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

package com.davils.kontinuity.hedge.internal

import com.davils.kontinuity.hedge.HedgeDelay
import com.davils.kontinuity.hedge.HedgeSnapshot
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.concurrency.Guard
import com.davils.kore.core.concurrency.guard
import kotlin.time.Duration

internal class Ledger(delay: HedgeDelay) {
    private val guard: Guard = guard()
    private val pacer: Pacer = Pacer.of(delay)
    private var primarySucceeded: Long = 0L
    private var hedgeSucceeded: Long = 0L
    private var failed: Long = 0L
    private var cancelled: Long = 0L
    private var hedgesStarted: Long = 0L

    val delay: Duration get() = guard.locked { pacer.current }

    fun primarySucceeded(duration: Duration): Duration = guard.locked {
        primarySucceeded += 1L
        pacer.observe(duration)
        pacer.current
    }

    fun hedgeSucceeded() {
        guard.locked { hedgeSucceeded += 1L }
    }

    fun failed() {
        guard.locked { failed += 1L }
    }

    fun cancelled() {
        guard.locked { cancelled += 1L }
    }

    fun hedgeStarted() {
        guard.locked { hedgesStarted += 1L }
    }

    fun snapshot(): HedgeSnapshot = guard.locked {
        HedgeSnapshot(
            primarySucceeded = primarySucceeded,
            hedgeSucceeded = hedgeSucceeded,
            failed = failed,
            cancelled = cancelled,
            hedgesStarted = hedgesStarted,
            delay = pacer.current,
        )
    }
}
