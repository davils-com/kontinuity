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

package com.davils.kontinuity.retry

import kotlin.time.Duration

/**
 * Chooses the wait before the next attempt by the failure or the result of the attempt before it, such as the
 * `Retry-After` of a response, as the `IntervalBiFunction` of resilience4j does.
 *
 * A [Retry] asks its interval before every retry and waits the backoff of its configuration when the interval
 * returns null. The interval is foreign code: when it throws, the retry waits the backoff instead, and a fatal error
 * passes through. Every wait it returns is clamped to `[0, Backoff.HARD_MAXIMUM_DELAY]`, so a negative wait does not
 * wait at all and an infinite one waits one day. The interval may be called from many coroutines and threads at once
 * and must be safe for that; the failure and the result it receives may come from an attacker.
 *
 * @since 1.0.0
 */
public fun interface RetryInterval {
    /**
     * Returns the wait before [attempt], or null for the backoff of the configuration.
     *
     * @param attempt the number of the attempt that follows the wait: 2 before the first retry.
     * @param failure the failure of the attempt before, or null if it returned a result.
     * @param result the result of the attempt before, or null if it failed or returned null.
     * @return the wait, or null for the backoff.
     * @since 1.0.0
     */
    public fun delayBefore(attempt: Int, failure: Throwable?, result: Any?): Duration?
}
