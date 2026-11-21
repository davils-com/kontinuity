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

package com.davils.kontinuity.testing

import com.davils.konfetti.testing.SeededEntropy
import com.davils.kontinuity.core.Environment
import com.davils.kontinuity.core.EnvironmentBuilder
import com.davils.kontinuity.core.environment
import kotlinx.coroutines.CoroutineScope
import kotlin.time.TimeSource

private const val DEFAULT_SEED: Long = 42L

/**
 * Returns an environment for tests: the time of [timeSource], deterministic randomness from [seed] and, when given,
 * [scope] for background work.
 *
 * Hand in the time source of the test scheduler, so that waits, windows and timers of the instances follow the
 * virtual time of the test: `testEnvironment(testCoroutineScheduler.timeSource, scope = this)`.
 *
 * @param timeSource the time of the instances.
 * @param scope the scope of background work, or null for none.
 * @param seed the seed of the randomness of jitter and hedging; 42 by default.
 * @param configure changes what else differs, such as `events(bus)`.
 * @return the environment.
 * @since 1.0.0
 */
public fun testEnvironment(
    timeSource: TimeSource.WithComparableMarks,
    scope: CoroutineScope? = null,
    seed: Long = DEFAULT_SEED,
    configure: EnvironmentBuilder.() -> Unit = {},
): Environment = environment {
    this.timeSource = timeSource
    entropy = SeededEntropy(seed)
    if (scope != null) scope(scope)
    configure()
}
