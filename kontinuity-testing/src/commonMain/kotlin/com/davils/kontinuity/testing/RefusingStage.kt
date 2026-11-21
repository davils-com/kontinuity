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

package com.davils.kontinuity.testing

import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.Stage

/**
 * A stage that refuses every call with a [TestRejection], to check how an outer stage treats a rejection.
 *
 * @property name the name of the stage; `refusing` by default.
 * @since 1.0.0
 */
public class RefusingStage(override val name: InstanceName = InstanceName.of("refusing")) : Stage {
    override val kind: ResilienceKind get() = ResilienceKind.Bulkhead

    /**
     * Refuses the call without running [block].
     *
     * @param T the type of the result.
     * @param block the call, which never runs.
     * @return never.
     * @throws TestRejection always.
     * @since 1.0.0
     */
    override suspend fun <T> execute(block: suspend () -> T): T = throw TestRejection(name, kind)

    /**
     * Shows the name.
     *
     * @return the stage as text.
     * @since 1.0.0
     */
    override fun toString(): String = "RefusingStage($name)"
}
