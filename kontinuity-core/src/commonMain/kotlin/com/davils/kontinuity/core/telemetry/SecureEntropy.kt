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

package com.davils.kontinuity.core.telemetry

import com.davils.konfetti.core.bus.Entropy
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.identity.generator.UuidV4Generator

private const val RANDOM_LOW_BITS: Long = 0x3FFF_FFFF_FFFF_FFFF
private const val HIGH_SHIFT: Int = 62

/**
 * Secure randomness from the random bits of the version 4 UUIDs of Kore, the default entropy of kontinuity.
 *
 * @since 1.0.0
 */
@DavilsInternal
public object SecureEntropy : Entropy {
    /**
     * Returns 64 secure random bits.
     *
     * @return the bits.
     * @since 1.0.0
     */
    override fun nextLong(): Long {
        val uuid = UuidV4Generator.next()
        val high = uuid.mostSignificantBits ushr HIGH_SHIFT shl HIGH_SHIFT
        return high or (uuid.leastSignificantBits and RANDOM_LOW_BITS)
    }
}
