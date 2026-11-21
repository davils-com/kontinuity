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

import java.time.Duration

plugins {
    id("kontinuity.base")
}

kreate {
    assurance {
        standards {
            enabled = true
            reviewEvery = Duration.ofDays(365)

            inventory {
                includes.set(setOf("*.md", "kontinuity-*/src/**"))
            }

            github("resilience4j/resilience4j") {
                note = "The feature set kontinuity covers: circuit breaker, retry, rate limiter, bulkheads, time " +
                    "limiter, cache, hedge, fallback, registries, events and metrics."
                tags = """^v\d+\.\d+\.\d+$"""
            }
            rfc(9562) {
                note = "UUID version 4: the secure randomness behind the default entropy of jitter and hedging."
            }
            unicode {
                note = "Instance names refuse control characters and lone surrogates and are shown through SafeText."
            }
        }
    }
}
