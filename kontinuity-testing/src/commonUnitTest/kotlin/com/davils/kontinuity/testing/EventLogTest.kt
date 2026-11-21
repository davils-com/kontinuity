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

package com.davils.kontinuity.testing

import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableSharedFlow

private const val FIRST: Int = 1
private const val TEXT: String = "text"

@OptIn(ExperimentalKotest::class)
class EventLogTest : FunSpec({
    coroutineTestScope = true

    test("a log records the events emitted while it listens") {
        val flow = MutableSharedFlow<Any>(extraBufferCapacity = 8)
        val log = recordEvents(flow)
        flow.tryEmit(FIRST)
        flow.tryEmit(TEXT)
        testCoroutineScheduler.runCurrent()
        log.stop()
        flow.tryEmit(TEXT)
        testCoroutineScheduler.runCurrent()
        log.events shouldBe listOf(FIRST, TEXT)
        log.only<String>() shouldBe listOf(TEXT)
        log.toString() shouldBe "EventLog(2 events)"
    }

    test("a test environment follows the given time and seed") {
        val timeSource = testCoroutineScheduler.timeSource
        val environment = testEnvironment(timeSource, scope = this, seed = 1L)
        environment.timeSource shouldBe timeSource
        environment.scope shouldBe this
        testEnvironment(timeSource).scope shouldBe null
    }
})
