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

package com.davils.kontinuity.bulkhead.internal

private const val STALE: Int = 1
private const val FRESH: Int = 2

internal class OvertakenSource : LevelSource {
    var reads: Int = 0

    override val version: Long get() = 2L

    override fun levels(): Levels {
        reads += 1
        val level = STALE.takeIf { reads == 1 } ?: FRESH
        return Levels(version = reads.toLong(), first = level, second = level)
    }
}
