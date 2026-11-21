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

package com.davils.kontinuity.timelimiter

import com.davils.kontinuity.core.CallRejectedException
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind

/**
 * A soft time limiter refused a call because [maxDetached] of its calls still run detached on the scope of its
 * environment; the call never ran.
 *
 * @param instance the name of the limiter.
 * @property maxDetached the most detached calls of the limiter.
 * @since 1.0.0
 */
public class DetachedLimitExceededException internal constructor(
    instance: InstanceName,
    public val maxDetached: Int,
) : CallRejectedException(
    instance = instance,
    kind = ResilienceKind.TimeLimiter,
    message = "The time limiter $instance runs $maxDetached detached calls already and refuses further calls",
)
