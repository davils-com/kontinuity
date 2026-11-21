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

package com.davils.kontinuity.ratelimiter.internal

internal class Pace(periodNanos: Long, val limit: Int) {
    private val quotient: Long = periodNanos / limit
    private val remainder: Long = periodNanos % limit

    fun span(tokens: Long): FineTime {
        val parts = tokens * remainder
        val whole = tokens * quotient + parts / limit
        return FineTime(nanos = whole, fraction = parts % limit)
    }

    fun plus(time: FineTime, span: FineTime): FineTime {
        val fraction = time.fraction + span.fraction
        val nanos = time.nanos + span.nanos + fraction / limit
        return FineTime(nanos = nanos, fraction = fraction % limit)
    }

    fun minus(time: FineTime, span: FineTime): FineTime {
        val fraction = time.fraction - span.fraction + limit
        val nanos = time.nanos - span.nanos - 1L + fraction / limit
        return FineTime(nanos = nanos, fraction = fraction % limit)
    }

    fun later(time: FineTime, nanos: Long): FineTime {
        if (time.isBefore(nanos)) return FineTime.of(nanos)
        return time
    }
}
