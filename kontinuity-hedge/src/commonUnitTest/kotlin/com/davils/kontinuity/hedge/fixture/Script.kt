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

package com.davils.kontinuity.hedge.fixture

import com.davils.kontinuity.testing.CountingStage
import kotlinx.coroutines.delay

internal class Script(vararg steps: Step) {
    private val plan: List<Step> = steps.toList()
    private var next: Int = 0

    val copies: CountingStage = CountingStage()

    val cancelled: Int get() = copies.calls - copies.successes - copies.failures

    suspend fun run(): Int = copies.execute { step() }

    private suspend fun step(): Int {
        val number = next
        next += 1
        val step = plan[number]
        delay(step.latency)
        val failure = step.failure
        if (failure != null) throw failure
        return number
    }
}
