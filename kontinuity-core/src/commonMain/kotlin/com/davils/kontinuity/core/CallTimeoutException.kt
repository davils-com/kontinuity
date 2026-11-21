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

import kotlin.time.Duration

/**
 * A call that did not finish within the timeout of a time limiter.
 *
 * It is a failure, not a rejection: the call ran and took too long, so a circuit breaker records it and a retry may
 * repeat it. A fallback can select it with `onTimeout`.
 *
 * @property instance the name of the time limiter.
 * @property timeout the timeout the call exceeded.
 * @since 1.0.0
 */
public class CallTimeoutException(
    public val instance: InstanceName,
    public val timeout: Duration,
) : RuntimeException("The time limiter $instance cancelled a call after $timeout")
