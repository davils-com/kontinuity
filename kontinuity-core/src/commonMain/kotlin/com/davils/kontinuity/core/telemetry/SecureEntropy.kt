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
import com.davils.kore.core.random.SecureEntropy as PlatformEntropy

/**
 * The secure random generator of the platform as an entropy of konfetti, the default entropy of kontinuity.
 *
 * Since 1.1.0 it draws from the `SecureEntropy` of Kore, `SecureRandom` on the JVM and `crypto.getRandomValues` on
 * Wasm, instead of from the random bits of version 4 UUIDs.
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
    override fun nextLong(): Long = PlatformEntropy.nextLong()
}
