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

/**
 * An instance that protects a suspending call, such as a circuit breaker, a retry or a bulkhead.
 *
 * A stage runs the call in the coroutine of its caller, unless its documentation says otherwise. The cancellation of
 * the caller is rethrown at once and never counted as a failure; a refusal of the call is a
 * [CallRejectedException]. Stages nest: `retry.execute { breaker.execute { call() } }` retries the call through the
 * breaker. The functions in `Stages.kt` add the operator form `stage { call() }`, [attempt] and decorators.
 *
 * @since 1.0.0
 */
public interface Stage : Instance {
    /**
     * Runs [block] under the protection of this stage and returns its result.
     *
     * @param T the type of the result.
     * @param block the protected call.
     * @return the result of [block], or what the stage puts in its place, such as the result of a later attempt.
     * @throws CallRejectedException if the stage refuses the call, such as an open circuit breaker.
     * @throws Throwable whatever [block] throws and the stage passes on.
     * @since 1.0.0
     */
    public suspend fun <T> execute(block: suspend () -> T): T
}
