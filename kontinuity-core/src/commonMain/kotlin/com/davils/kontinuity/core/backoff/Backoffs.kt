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

@file:OptIn(DavilsInternal::class)

package com.davils.kontinuity.core.backoff

import com.davils.konfetti.core.bus.Entropy
import com.davils.kontinuity.core.telemetry.nextFraction
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.backoff.decorrelatedJitter
import com.davils.kore.core.backoff.equalJitter
import com.davils.kore.core.backoff.fullJitter
import com.davils.kore.core.backoff.grownDelay
import com.davils.kore.core.backoff.proportionalJitter
import kotlin.time.Duration

private const val DEFAULT_MULTIPLIER: Double = 2.0
private const val DEFAULT_GROWTH: Int = 32
private const val DEFAULT_FACTOR: Double = 0.5

/**
 * Returns a backoff with the same [delay] before every attempt.
 *
 * @param delay the delay, from zero to [Backoff.HARD_MAXIMUM_DELAY].
 * @return the backoff.
 * @throws com.davils.kore.core.validation.report.ValidationException if [delay] is negative, infinite or above the
 *   hard ceiling.
 * @since 1.0.0
 */
public fun fixed(delay: Duration): Backoff = Backoff.Fixed(delay)

/**
 * Returns a backoff whose delay starts at [initial] and grows by [multiplier] per attempt, at most to [maximum].
 *
 * @param initial the first delay, positive.
 * @param multiplier the growth per attempt, from 1 to [Backoff.HARD_MULTIPLIER]; 2 by default.
 * @param maximum the longest delay, at least [initial] and at most [Backoff.HARD_MAXIMUM_DELAY]; 32 times [initial]
 *   by default, but never above the hard ceiling.
 * @return the backoff.
 * @throws com.davils.kore.core.validation.report.ValidationException if a value is out of its bounds.
 * @since 1.0.0
 */
public fun exponential(
    initial: Duration,
    multiplier: Double = DEFAULT_MULTIPLIER,
    maximum: Duration = (initial * DEFAULT_GROWTH).coerceAtMost(Backoff.HARD_MAXIMUM_DELAY),
): Backoff = Backoff.Exponential(initial = initial, multiplier = multiplier, maximum = maximum)

/**
 * Returns this backoff with its delays spread by [jitter].
 *
 * @receiver the backoff whose delays are spread.
 * @param jitter how the delays are spread, such as [Jitter.Full] or [proportional].
 * @return the jittered backoff.
 * @since 1.0.0
 */
public fun Backoff.jittered(jitter: Jitter): Backoff = Backoff.Jittered(base = this, jitter = jitter)

/**
 * Returns a jitter that moves a delay by at most [factor] of itself in either direction.
 *
 * @param factor how far a delay may move, in `(0, 1]`; 0.5 by default.
 * @return the jitter.
 * @throws com.davils.kore.core.validation.report.ValidationException if [factor] is out of `(0, 1]`, when the
 *   jittered backoff is created.
 * @since 1.0.0
 */
public fun proportional(factor: Double = DEFAULT_FACTOR): Jitter = Jitter.Proportional(factor)

/**
 * Returns the delay before attempt [attempt], given the delay before the previous attempt.
 *
 * @receiver the backoff.
 * @param attempt the attempt that comes next, from 2 on; the first attempt has no delay.
 * @param previous the delay before the previous attempt, zero before the second attempt.
 * @param entropy the randomness of a jittered backoff; a failing entropy is replaced by secure randomness.
 * @return the delay, from zero to [Backoff.maximum].
 * @since 1.0.0
 */
@DavilsInternal
public fun Backoff.delayBefore(attempt: Int, previous: Duration, entropy: Entropy): Duration {
    val delay = rawDelay(attempt = attempt.coerceAtLeast(2), previous = previous, entropy = entropy)
    return delay.coerceIn(minimumValue = Duration.ZERO, maximumValue = maximum)
}

private fun Backoff.rawDelay(attempt: Int, previous: Duration, entropy: Entropy): Duration = when (this) {
    is Backoff.Fixed -> delay
    is Backoff.Exponential -> exponentialDelay(attempt)
    is Backoff.Jittered -> jitteredDelay(attempt = attempt, previous = previous, entropy = entropy)
}

private fun Backoff.Exponential.exponentialDelay(attempt: Int): Duration =
    grownDelay(initial = initial, multiplier = multiplier, step = attempt - 2, maximum = maximum)

private fun Backoff.Jittered.jitteredDelay(attempt: Int, previous: Duration, entropy: Entropy): Duration {
    val delay = base.delayBefore(attempt = attempt, previous = previous, entropy = entropy)
    val fraction = entropy.nextFraction()
    return when (val spread = jitter) {
        is Jitter.Proportional -> proportionalJitter(delay = delay, factor = spread.factor, fraction = fraction)
        Jitter.Full -> fullJitter(delay = delay, fraction = fraction)
        Jitter.Equal -> equalJitter(delay = delay, fraction = fraction)
        Jitter.Decorrelated -> decorrelated(previous = previous, fraction = fraction, entropy = entropy)
    }
}

private fun Backoff.Jittered.decorrelated(previous: Duration, fraction: Double, entropy: Entropy): Duration {
    val initial = base.delayBefore(attempt = 2, previous = Duration.ZERO, entropy = entropy)
    return decorrelatedJitter(initial = initial, previous = previous, maximum = base.maximum, fraction = fraction)
}
