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

package com.davils.kontinuity.core.registry

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.fixture.Probe
import com.davils.kontinuity.core.fixture.ProbeConfig
import com.davils.kontinuity.core.fixture.ProbeRegistryBuilder
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private const val PAYMENTS: String = "payments"
private const val STRICT: String = "strict"
private const val MISSING: String = "missing"
private const val VIOLATIONS: Int = 5
private const val STRICT_LEVEL: Int = 7
private const val TWO: Int = 2

@OptIn(ExperimentalKotest::class)
class RegistryTest : FunSpec({
    coroutineTestScope = true

    test("configurations start from the defaults or from an earlier configuration") {
        val settings = settings {
            config(PAYMENTS) { label = PAYMENTS }
            defaults { level = TWO }
            config(STRICT, base = PAYMENTS) { level = STRICT_LEVEL }
        }
        settings.defaults.level shouldBe TWO
        settings.config(RegistrySettings.DEFAULT_CONFIG) shouldBeSameInstanceAs settings.defaults
        settings.config(PAYMENTS)?.level shouldBe TWO
        settings.config(STRICT)?.level shouldBe STRICT_LEVEL
        settings.config(STRICT)?.label shouldBe PAYMENTS
        settings.config(MISSING) shouldBe null
        settings.configNames shouldContainExactly listOf(PAYMENTS, STRICT)
        settings.toString() shouldContain "configs=2"
    }

    test("every problem of every block is reported at once") {
        val refusal = shouldThrow<ValidationException> {
            settings {
                defaults { level = 0 }
                config("Bad Name") { level = 1 }
                config("default") { level = 1 }
                config("orphan", base = "unknown") { level = ProbeConfig.MAXIMUM_LEVEL + 1 }
                limits { maximumInstances = 0 }
            }
        }
        refusal.report.violations.size shouldBe VIOLATIONS
    }

    test("a second block for the limits or the defaults is a duplicate") {
        shouldThrow<ValidationException> {
            settings {
                limits { maximumInstances = 1 }
                limits { maximumInstances = TWO }
            }
        }
        shouldThrow<ValidationException> {
            settings {
                defaults { level = 1 }
                defaults { level = TWO }
            }
        }
    }

    test("a registry has at most as many configurations as its limit") {
        settings {
            limits { maximumConfigs = 1 }
            config("one") {}
        }.configs.size shouldBe 1
        shouldThrow<ValidationException> {
            settings {
                limits { maximumConfigs = 1 }
                config("one") {}
                config("two") {}
            }
        }
    }

    test("a registry creates an instance once per name") {
        val registry = registry { config(STRICT) { level = STRICT_LEVEL } }
        val first = registry.getOrCreate(name = PAYMENTS, config = STRICT)
        registry.getOrCreate(name = PAYMENTS, config = RegistrySettings.DEFAULT_CONFIG) shouldBeSameInstanceAs first
        first.config.level shouldBe STRICT_LEVEL
        registry.find(PAYMENTS) shouldBeSameInstanceAs first
        registry.find(MISSING) shouldBe null
        registry.all shouldContainExactly listOf(first)
        registry.toString() shouldBe "Registry(hedge, instances=1)"
    }

    test("an unknown configuration or an invalid name is refused") {
        val registry = registry {}
        shouldThrow<IllegalArgumentException> { registry.getOrCreate(name = PAYMENTS, config = MISSING) }
        shouldThrow<IllegalArgumentException> {
            registry.getOrCreate(
                name = "Bad Name",
                config = RegistrySettings.DEFAULT_CONFIG,
            )
        }
    }

    test("a full registry refuses new instances but keeps answering for existing ones") {
        val registry = registry { limits { maximumInstances = 1 } }
        val first = registry.getOrCreate(name = "first", config = ProbeConfig.BuiltIn)
        val refusal = shouldThrow<InstanceLimitException> {
            registry.getOrCreate(
                name = "second",
                config = ProbeConfig.BuiltIn,
            )
        }
        refusal.kind shouldBe ResilienceKind.Hedge
        refusal.maximum shouldBe 1
        refusal.message shouldBe "The hedge registry holds 1 instances already, its limit"
        registry.getOrCreate(name = "first", config = ProbeConfig.BuiltIn) shouldBeSameInstanceAs first
        shouldThrow<InstanceLimitException> { registry.replace(probe("second")) }
    }

    test("removing and replacing change the instances and are reported") {
        val registry = registry {}
        val events = mutableListOf<RegistryEvent<Probe>>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            registry.events.collect { event -> events += event }
        }
        val first = registry.getOrCreate(name = PAYMENTS, config = ProbeConfig.BuiltIn)
        val second = probe(PAYMENTS)
        registry.replace(second) shouldBeSameInstanceAs first
        registry.replace(probe("orders")) shouldBe null
        registry.remove(PAYMENTS) shouldBeSameInstanceAs second
        registry.remove(PAYMENTS) shouldBe null
        testCoroutineScheduler.runCurrent()
        collector.cancel()
        events.map { event -> event.toString() } shouldContainExactly listOf(
            "Added(hedge payments)",
            "Replaced(hedge payments)",
            "Added(hedge orders)",
            "Removed(hedge payments)",
        )
        events[1].shouldBeInstanceOf<RegistryEvent.Replaced<Probe>>().previous shouldBeSameInstanceAs first
        events[1].instance shouldBeSameInstanceAs second
    }

    test("limits are equal by value") {
        val limits = instanceLimits { maximumInstances = TWO }
        limits shouldBe instanceLimits { maximumInstances = TWO }
        limits.hashCode() shouldBe instanceLimits { maximumInstances = TWO }.hashCode()
        (limits == InstanceLimits.Default) shouldBe false
        val other = instanceLimits {
            maximumInstances = TWO
            maximumConfigs = TWO
        }
        (limits == other) shouldBe false
        limits.equals(PAYMENTS) shouldBe false
        limits.toString() shouldBe "InstanceLimits(maximumInstances=2, maximumConfigs=64)"
        shouldThrow<ValidationException> { instanceLimits { maximumConfigs = InstanceLimits.HARD_CONFIGS + 1 } }
        instanceLimits { maximumInstances = InstanceLimits.HARD_INSTANCES }.maximumInstances shouldBe
            InstanceLimits.HARD_INSTANCES
    }

    test("a limits builder cannot change after it was evaluated") {
        val builder = InstanceLimitsBuilder()
        builder.build()
        shouldThrow<IllegalStateException> { builder.maximumInstances = 1 }
        shouldThrow<IllegalStateException> { builder.maximumConfigs = 1 }
    }

    test("a registry builder cannot change after it was evaluated") {
        val builder = ProbeRegistryBuilder()
        builder.build()
        shouldThrow<IllegalStateException> { builder.limits {} }
        shouldThrow<IllegalStateException> { builder.defaults {} }
        shouldThrow<IllegalStateException> { builder.config("late") {} }
    }
})

private fun settings(configure: ProbeRegistryBuilder.() -> Unit): RegistrySettings<ProbeConfig> {
    val builder = ProbeRegistryBuilder()
    builder.configure()
    return builder.build()
}

private fun registry(configure: ProbeRegistryBuilder.() -> Unit): Instances<ProbeConfig, Probe> = Instances(
    kind = ResilienceKind.Hedge,
    settings = settings(configure),
    environment = Environment.Default,
    create = ::Probe,
)

private fun probe(name: String): Probe = Probe(InstanceName.of(name), ProbeConfig.BuiltIn)

private fun instanceLimits(configure: InstanceLimitsBuilder.() -> Unit): InstanceLimits {
    val builder = InstanceLimitsBuilder()
    builder.configure()
    return builder.build()
}
