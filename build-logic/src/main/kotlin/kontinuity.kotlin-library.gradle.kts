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
    id("org.jetbrains.kotlin.multiplatform")
    id("kontinuity.base")
    id("kontinuity.dependency-locking")
    id("kontinuity.git-hooks")
    id("kontinuity.trivy")
    id("kontinuity.assurance")
    id("kontinuity.platform")
    id("kontinuity.web-tools")
    id("kontinuity.testing")
    id("kontinuity.detekt")
    id("kontinuity.coverage")
    id("kontinuity.api-validation")
    id("kontinuity.docs")
    id("kontinuity.publish")
    id("kontinuity.archives")
    id("kontinuity.tool-classpaths")
}

kreate {
    assurance {
        advisories {
            configurations = setOf(
                "jvmCompileClasspath",
                "jvmRuntimeClasspath",
                "jvmUnitTestCompileClasspath",
                "jvmUnitTestRuntimeClasspath",
                "wasmJsCompileClasspath",
                "wasmJsRuntimeClasspath",
                "wasmJsTestCompileClasspath",
                "wasmJsTestRuntimeClasspath",
            )
        }
    }
}
