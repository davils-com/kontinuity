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

plugins {
    id("kontinuity.kotlin-library")
}

kreate {
    project {
        description = "The core of kontinuity, the resilience library of Davils: instance names, call outcomes, " +
            "rejections, failure predicates, fallbacks, registries and the shared event and metric plumbing, for " +
            "the JVM and WebAssembly."
    }
}

dependencies {
    commonMainApi(libs.kore.core)
    commonMainApi(libs.konfetti.core)
    commonMainApi(libs.kollect.core)
    commonMainApi(libs.kotlinx.coroutines.core)
}

val testConfigurations: Set<String> = setOf("commonUnitTestImplementation", "wasmJsTestImplementation")

configurations.matching { configuration -> configuration.name in testConfigurations }.configureEach {
    dependencies.add(project.dependencies.project(":kontinuity-testing"))
}
