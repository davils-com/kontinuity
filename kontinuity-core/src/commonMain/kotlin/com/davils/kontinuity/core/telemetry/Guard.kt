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

import com.davils.kore.core.annotation.DavilsInternal

/**
 * A mutual exclusion lock for the state of an instance: a reentrant lock on the JVM and a plain call on Wasm, which
 * runs on one thread.
 *
 * @since 1.0.0
 */
@DavilsInternal
@Deprecated(
    message = "Kore shares one guard with every Davils library",
    replaceWith = ReplaceWith(expression = "Guard", imports = ["com.davils.kore.core.concurrency.Guard"]),
)
public interface Guard {
    /**
     * Runs [block] while holding the lock.
     *
     * @param T the type of the result.
     * @param block the work that needs the lock; it must not suspend or call foreign code.
     * @return the result of [block].
     * @since 1.0.0
     */
    public fun <T> locked(block: () -> T): T
}
