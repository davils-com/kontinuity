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

package com.davils.kontinuity.core.filter

/**
 * What a [FailureFilter] decides about a failure.
 *
 * @since 1.0.0
 */
public enum class FailureVerdict {
    /**
     * The failure counts: a circuit breaker records it as a failure and a retry repeats the call.
     *
     * @since 1.0.0
     */
    Recorded,

    /**
     * The failure is ignored: a circuit breaker neither counts it as a failure nor as a success and a retry passes it
     * on at once. Every rejection of kontinuity is ignored.
     *
     * @since 1.0.0
     */
    Ignored,

    /**
     * The failure matches no rule of a filter that records only listed failures: a circuit breaker counts the call as
     * a success, as resilience4j does, and a retry passes the failure on at once.
     *
     * @since 1.0.0
     */
    Passed,
}
