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

import com.davils.kore.core.annotation.DavilsDsl
import com.davils.kore.core.dsl.ValidatingBuilder
import kotlin.time.Duration

/**
 * Builds a [TimeLimiterConfig]; every property starts from the configuration the builder starts from.
 *
 * @since 1.0.0
 */
@DavilsDsl
public class TimeLimiterConfigBuilder internal constructor() : ValidatingBuilder<TimeLimiterConfig>() {
    override val subject: String get() = "time limiter configuration"

    /**
     * See [TimeLimiterConfig.timeout].
     *
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public var timeout: Duration = TimeLimiterConfig.DEFAULT_TIMEOUT
        set(value) {
            checkOpen()
            field = value
        }

    /**
     * See [TimeLimiterConfig.strategy].
     *
     * @throws IllegalStateException if this builder was evaluated already.
     * @since 1.0.0
     */
    public var strategy: TimeoutStrategy = TimeoutStrategy.Hard
        set(value) {
            checkOpen()
            field = value
        }

    internal fun from(base: TimeLimiterConfig) {
        timeout = base.timeout
        strategy = base.strategy
    }

    override fun assemble(): TimeLimiterConfig = TimeLimiterConfig(this)
}
