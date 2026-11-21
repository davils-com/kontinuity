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

package com.davils.kontinuity.cache.fixture

import com.davils.kollect.core.MeterRegistry
import com.davils.kollect.core.name.MetricName
import com.davils.kollect.core.snapshot.GaugePoint
import com.davils.kollect.core.snapshot.HistogramPoint
import com.davils.kollect.core.snapshot.Point
import com.davils.kollect.core.snapshot.SumPoint

internal fun MeterRegistry.sum(name: String, vararg labels: String): Double {
    val point = pointOf(name, labels.toList()) as? SumPoint
    return point?.value ?: 0.0
}

internal fun MeterRegistry.gauge(name: String, vararg labels: String): Double? {
    val point = pointOf(name, labels.toList()) as? GaugePoint
    return point?.value
}

internal fun MeterRegistry.recorded(name: String, vararg labels: String): Long {
    val point = pointOf(name, labels.toList()) as? HistogramPoint
    return point?.count ?: 0L
}

private fun MeterRegistry.pointOf(name: String, labels: List<String>): Point? {
    val family = snapshot().family(MetricName.of(name)) ?: return null
    return family.points.firstOrNull { point -> point.labels.values == labels }
}
