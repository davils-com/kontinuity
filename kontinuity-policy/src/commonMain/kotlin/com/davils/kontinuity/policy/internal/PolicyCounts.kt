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

package com.davils.kontinuity.policy.internal

internal class PolicyCounts {
    var successful: Long = 0L
        private set
    var failed: Long = 0L
        private set
    var rejected: Long = 0L
        private set
    var cancelled: Long = 0L
        private set

    fun count(outcome: PolicyOutcome) {
        when (outcome) {
            PolicyOutcome.Successful -> successful += 1L
            PolicyOutcome.Failed -> failed += 1L
            PolicyOutcome.Rejected -> rejected += 1L
            PolicyOutcome.Cancelled -> cancelled += 1L
        }
    }
}
