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
import com.davils.kore.core.failure.contained

private const val FRACTION_SHIFT: Int = 11
private const val FRACTION_SCALE: Double = 1.0 / (1L shl 53)

/**
 * Returns a uniform random fraction in `[0, 1)` from 53 bits of this entropy.
 *
 * A failing entropy is contained: the fraction then comes from [SecureEntropy]. Fatal errors are rethrown.
 *
 * @receiver the entropy, which may be foreign code.
 * @return the fraction.
 * @since 1.0.0
 */
@DavilsInternal
public fun Entropy.nextFraction(): Double {
    val entropy = this
    val bits = contained { entropy.nextLong() }.getOrElse { SecureEntropy.nextLong() }
    return (bits ushr FRACTION_SHIFT) * FRACTION_SCALE
}
