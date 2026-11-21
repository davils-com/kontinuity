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

package com.davils.kontinuity.timelimiter.internal

import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.timelimiter.TimeLimiterConfig
import com.davils.kontinuity.timelimiter.TimeoutStrategy
import kotlin.time.Duration

internal fun limitOf(
    name: InstanceName,
    config: TimeLimiterConfig,
    environment: Environment,
    reporter: Reporter,
): Limit = when (val strategy = config.strategy) {
    TimeoutStrategy.Hard -> hardLimitOf(config.timeout)

    is TimeoutStrategy.Soft -> softLimitOf(
        name = name,
        timeout = config.timeout,
        strategy = strategy,
        environment = environment,
        reporter = reporter,
    )
}

private fun hardLimitOf(timeout: Duration): Limit {
    if (timeout.isInfinite()) return Unlimited
    return HardLimit(timeout)
}

private fun softLimitOf(
    name: InstanceName,
    timeout: Duration,
    strategy: TimeoutStrategy.Soft,
    environment: Environment,
    reporter: Reporter,
): Limit {
    val scope = requireNotNull(environment.scope) {
        "The time limiter $name needs the scope of its environment for its soft strategy"
    }
    if (timeout.isInfinite()) return Unlimited
    val work = DetachedWork(scope = scope, maxDetached = strategy.maxDetached, reporter = reporter)
    return SoftLimit(name = name, timeout = timeout, strategy = strategy, work = work)
}
