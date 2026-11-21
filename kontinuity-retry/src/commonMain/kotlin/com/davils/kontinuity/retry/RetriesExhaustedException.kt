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

import com.davils.kontinuity.core.InstanceName

/**
 * A retry gave up because every attempt returned a result its result filter counts as a failure, and its
 * configuration asks for a failure with [RetryConfig.isFailingAfterMaxAttempts].
 *
 * It is no rejection: the retry ran every attempt it allows. It holds the simple name of the type of the last result,
 * never the result itself, so that no data of a call leaks into a log; the retry hands the result to
 * [RetryConfig.onDiscardedResult] before it throws.
 *
 * @property instance the name of the retry.
 * @property attempts how many attempts the retry ran.
 * @property resultType the simple name of the type of the last result, or `null` for a null result.
 * @since 1.0.0
 */
public class RetriesExhaustedException internal constructor(
    public val instance: InstanceName,
    public val attempts: Int,
    public val resultType: String,
) : RuntimeException(
    "The retry $instance gave up after $attempts attempts, because the result filter failed every result " +
        "(last result: $resultType)",
)
