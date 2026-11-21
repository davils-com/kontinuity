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

package com.davils.kontinuity.ratelimiter

import com.davils.kontinuity.core.CallRejectedException
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import kotlin.time.Duration

/**
 * A rate limiter refused a call because its permits are not free within the timeout of the caller, or because the
 * caller would have to wait while the queue of waiting callers is full.
 *
 * Like every rejection of kontinuity, it is no failure of another component: a circuit breaker never records it and a
 * retry never repeats it by default. The message shows only the name of the limiter and numbers.
 *
 * @param instance the name of the limiter.
 * @property permits how many permits the caller asked for.
 * @property availableIn how long the caller would have had to wait for its permits when it was refused.
 * @since 1.0.0
 */
public class RequestNotPermittedException internal constructor(
    instance: InstanceName,
    public val permits: Int,
    public val availableIn: Duration,
) : CallRejectedException(
    instance = instance,
    kind = ResilienceKind.RateLimiter,
    message = "The rate limiter $instance permits no $permits permits now; they are free in $availableIn",
)
