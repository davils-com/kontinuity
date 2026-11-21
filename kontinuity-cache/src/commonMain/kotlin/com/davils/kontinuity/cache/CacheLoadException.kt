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

package com.davils.kontinuity.cache

import com.davils.kontinuity.core.InstanceName

/**
 * The load a call of [Cache.getOrLoad] waited for failed: another caller loaded the same key at the same time and its
 * loader threw [cause].
 *
 * Every waiting caller gets an exception of its own, so that no caller ever sees a stack trace or a suppressed
 * exception that another caller added; they share only [cause]. The caller that ran the loader gets the failure of
 * the loader itself. The message never shows the key or the message of the cause.
 *
 * @property instance the name of the cache.
 * @param cause the failure of the shared load.
 * @since 1.0.0
 */
public class CacheLoadException internal constructor(
    public val instance: InstanceName,
    cause: Throwable,
) : RuntimeException("The shared load of the cache $instance failed", cause)
