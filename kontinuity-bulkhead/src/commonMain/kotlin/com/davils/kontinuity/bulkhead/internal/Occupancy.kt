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

package com.davils.kontinuity.bulkhead.internal

import com.davils.kontinuity.bulkhead.DispatcherBulkheadConfig
import com.davils.kontinuity.bulkhead.DispatcherBulkheadSnapshot
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.concurrency.Guard
import com.davils.kore.core.concurrency.guard

internal class Occupancy(config: DispatcherBulkheadConfig) : LevelSource {
    private val guard: Guard = guard()
    private val counts: Counts = Counts()
    private val capacity: Int = config.maxConcurrentCalls + config.queueCapacity
    private var running: Int = 0
    private var queued: Int = 0
    private var changes: Long = 0L

    override val version: Long get() = guard.locked { changes }

    fun admit(): Boolean = guard.locked {
        val isAdmitted = running + queued < capacity
        if (isAdmitted) queued += 1
        if (!isAdmitted) counts.rejected += 1L
        changes += 1L
        isAdmitted
    }

    fun start() {
        guard.locked {
            queued -= 1
            running += 1
            counts.permitted += 1L
            changes += 1L
        }
    }

    fun finish() {
        guard.locked {
            running -= 1
            counts.finished += 1L
            changes += 1L
        }
    }

    fun cancel() {
        guard.locked {
            queued -= 1
            counts.cancelled += 1L
            changes += 1L
        }
    }

    override fun levels(): Levels = guard.locked { Levels(version = changes, first = running, second = queued) }

    fun snapshot(): DispatcherBulkheadSnapshot = guard.locked {
        DispatcherBulkheadSnapshot(
            running = running,
            queued = queued,
            remainingCapacity = capacity - running - queued,
            permitted = counts.permitted,
            rejected = counts.rejected,
            finished = counts.finished,
            cancelled = counts.cancelled,
        )
    }
}
