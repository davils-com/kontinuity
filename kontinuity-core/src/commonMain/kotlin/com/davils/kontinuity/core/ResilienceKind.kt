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
 * What an instance of kontinuity is; it names the instance in events, meters and exception messages.
 *
 * @property id the name of the kind in events and meters, such as `circuit-breaker`.
 * @since 1.0.0
 */
public enum class ResilienceKind(public val id: String) {
    /**
     * A circuit breaker.
     *
     * @since 1.0.0
     */
    CircuitBreaker("circuit-breaker"),

    /**
     * A retry.
     *
     * @since 1.0.0
     */
    Retry("retry"),

    /**
     * A rate limiter.
     *
     * @since 1.0.0
     */
    RateLimiter("rate-limiter"),

    /**
     * A semaphore bulkhead.
     *
     * @since 1.0.0
     */
    Bulkhead("bulkhead"),

    /**
     * A dispatcher bulkhead, the counterpart of a thread pool bulkhead.
     *
     * @since 1.0.0
     */
    DispatcherBulkhead("dispatcher-bulkhead"),

    /**
     * A time limiter.
     *
     * @since 1.0.0
     */
    TimeLimiter("time-limiter"),

    /**
     * A cache.
     *
     * @since 1.0.0
     */
    Cache("cache"),

    /**
     * A hedge.
     *
     * @since 1.0.0
     */
    Hedge("hedge"),

    /**
     * A fallback.
     *
     * @since 1.0.0
     */
    Fallback("fallback"),

    /**
     * A policy that composes other instances.
     *
     * @since 1.0.0
     */
    Policy("policy"),
}
