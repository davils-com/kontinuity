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

package com.davils.kontinuity.bulkhead.internal

internal enum class MeterScheme(
    val prefix: String,
    val component: String,
    val firstGauge: String,
    val firstText: String,
    val secondGauge: String,
    val secondText: String,
) {
    Bulkhead(
        prefix = "kontinuity.bulkhead",
        component = "a bulkhead",
        firstGauge = "available_concurrent_calls",
        firstText = "How many more calls a bulkhead would permit right now.",
        secondGauge = "waiting_calls",
        secondText = "How many calls wait for a permit of a bulkhead.",
    ),
    Dispatcher(
        prefix = "kontinuity.dispatcher_bulkhead",
        component = "a dispatcher bulkhead",
        firstGauge = "running",
        firstText = "How many calls of a dispatcher bulkhead run right now.",
        secondGauge = "queued",
        secondText = "How many admitted calls of a dispatcher bulkhead wait for their turn on the dispatcher.",
    ),
}
