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

package com.davils.kontinuity.core

import com.davils.kollect.core.meterRegistry
import com.davils.kollect.core.trace.Tracer
import com.davils.konfetti.testing.SeededEntropy
import com.davils.kontinuity.core.fixture.withBus
import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineScope
import kotlin.time.TimeSource

private const val CAPACITY: Int = 8

@OptIn(ExperimentalKotest::class)
class EnvironmentTest : FunSpec({
    coroutineTestScope = true

    test("the default environment has the monotonic time and nothing else") {
        val environment = Environment.Default
        environment.timeSource shouldBe TimeSource.Monotonic
        environment.events shouldBe null
        environment.meters shouldBe null
        environment.tracer shouldBeSameInstanceAs Tracer.None
        environment.scope shouldBe null
        environment.eventCapacity shouldBe Environment.DEFAULT_EVENT_CAPACITY
        environment.toString() shouldBe "Environment(events=false, meters=false, scope=false, eventCapacity=64)"
    }

    test("an environment takes what its block sets and keeps the rest of its base") {
        val timeSource = testCoroutineScheduler.timeSource
        val testScope: CoroutineScope = this
        withBus { bus ->
            val registry = meterRegistry()
            val entropy = SeededEntropy(1L)
            val first = environment {
                this.timeSource = timeSource
                this.entropy = entropy
                events(bus)
                meters(registry)
                scope(testScope)
            }
            val second = environment(first) { eventCapacity = CAPACITY }
            second.timeSource shouldBe timeSource
            second.entropy shouldBeSameInstanceAs entropy
            second.events shouldBeSameInstanceAs bus
            second.meters shouldBeSameInstanceAs registry
            second.scope shouldBeSameInstanceAs testScope
            second.eventCapacity shouldBe CAPACITY
            second.toString() shouldBe "Environment(events=true, meters=true, scope=true, eventCapacity=8)"
        }
    }

    test("the event capacity is at least 1 and at most its hard ceiling") {
        environment { eventCapacity = 1 }.eventCapacity shouldBe 1
        environment { eventCapacity = Environment.HARD_EVENT_CAPACITY }.eventCapacity shouldBe
            Environment.HARD_EVENT_CAPACITY
        shouldThrow<ValidationException> { environment { eventCapacity = 0 } }
        shouldThrow<ValidationException> { environment { eventCapacity = Environment.HARD_EVENT_CAPACITY + 1 } }
    }

    test("a builder cannot change after it was evaluated") {
        val testScope: CoroutineScope = this
        val builder = EnvironmentBuilder()
        builder.build()
        shouldThrow<IllegalStateException> { builder.eventCapacity = CAPACITY }
        shouldThrow<IllegalStateException> { builder.timeSource = TimeSource.Monotonic }
        shouldThrow<IllegalStateException> { builder.entropy = SeededEntropy(1L) }
        shouldThrow<IllegalStateException> { builder.tracer = Tracer.None }
        shouldThrow<IllegalStateException> { builder.meters(meterRegistry()) }
        shouldThrow<IllegalStateException> { builder.scope(testScope) }
    }
})
