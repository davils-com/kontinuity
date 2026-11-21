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
 * The refusal of a call by an instance of kontinuity, such as an open circuit breaker, a full bulkhead or an exhausted
 * rate limiter; the call itself never ran.
 *
 * Every component of kontinuity treats a rejection as no failure of its own: a circuit breaker never records it, a
 * retry never repeats it unless told to and a fallback can select it with `onRejected`. The message shows only the
 * kind and the name of the instance, which are safe to show.
 *
 * @property instance the name of the instance that refused the call.
 * @property kind what the instance is.
 * @param message the description of the refusal.
 * @since 1.0.0
 */
public open class CallRejectedException protected constructor(
    public val instance: InstanceName,
    public val kind: ResilienceKind,
    message: String,
) : RuntimeException(message)
