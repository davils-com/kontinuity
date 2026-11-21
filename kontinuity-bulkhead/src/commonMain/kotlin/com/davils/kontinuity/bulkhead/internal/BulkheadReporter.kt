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

import com.davils.kontinuity.bulkhead.BulkheadEvent
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.telemetry.EventSink
import com.davils.kore.core.annotation.DavilsInternal
import kotlin.time.Duration

internal class BulkheadReporter(
    private val name: InstanceName,
    private val sink: EventSink<BulkheadEvent>,
    private val meters: PoolMeters?,
    private val source: LevelSource,
) {
    fun permitted(waited: Duration) {
        meters?.call(PoolMeters.PERMITTED)
        meters?.waited(waited)
        sink.emit { BulkheadEvent.Permitted(name, waited) }
        changed()
    }

    fun rejected(isQueueFull: Boolean) {
        meters?.call(PoolMeters.REJECTED)
        sink.emit { BulkheadEvent.Rejected(name, isQueueFull) }
        changed()
    }

    fun finished(held: Duration) {
        meters?.call(PoolMeters.FINISHED)
        sink.emit { BulkheadEvent.Finished(name, held) }
        changed()
    }

    fun cancelled() {
        meters?.call(PoolMeters.CANCELLED)
        changed()
    }

    fun changed() {
        meters?.publish(source)
    }
}
