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

import com.davils.kontinuity.core.CallRejectedException
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind

/**
 * A [Bulkhead] or a [DispatcherBulkhead] refused a call because it was full; the call never ran.
 *
 * The message names only the kind and the name of the bulkhead and whether its queue was full, never a configured
 * value or anything of the call.
 *
 * @param instance the name of the bulkhead.
 * @param kind [ResilienceKind.Bulkhead] or [ResilienceKind.DispatcherBulkhead].
 * @property isQueueFull true if the queue of waiting calls was full; false if the call was not allowed to wait or its
 *   wait for a permit ran out.
 * @since 1.0.0
 */
public class BulkheadFullException internal constructor(
    instance: InstanceName,
    kind: ResilienceKind,
    public val isQueueFull: Boolean,
) : CallRejectedException(instance = instance, kind = kind, message = messageOf(instance, kind, isQueueFull))

private fun messageOf(instance: InstanceName, kind: ResilienceKind, isQueueFull: Boolean): String {
    val reason = when (isQueueFull) {
        true -> "its queue of waiting calls is full"
        false -> "no permit became free in time"
    }
    val subject = kind.id.replace(oldChar = '-', newChar = ' ')
    return "The $subject $instance is full and $reason"
}
