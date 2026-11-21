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

import com.davils.kollect.core.meterRegistry
import com.davils.kontinuity.bulkhead.fixture.gauge
import com.davils.kontinuity.core.InstanceName
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val NAME: String = "payments"
private const val FRESH: Int = 2

class PoolMetersTest : FunSpec({
    test("the gauges show the levels of the last change even when a change overtakes them") {
        val registry = meterRegistry()
        val meters = PoolMeters(registry, InstanceName.of(NAME), MeterScheme.Bulkhead)
        val source = OvertakenSource()
        meters.publish(source)
        source.reads shouldBe 2
        registry.gauge("kontinuity.bulkhead.available_concurrent_calls", NAME) shouldBe FRESH.toDouble()
        registry.gauge("kontinuity.bulkhead.waiting_calls", NAME) shouldBe FRESH.toDouble()
    }
})
