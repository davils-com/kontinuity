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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Starts a log of the events of [flow] in this scope; it subscribes before it returns.
 *
 * @receiver the scope the log listens in, such as the scope of a test.
 * @param E the type of the events.
 * @param flow the events, such as `breaker.events`.
 * @return the log.
 * @since 1.0.0
 */
public fun <E> CoroutineScope.recordEvents(flow: Flow<E>): EventLog<E> = EventLog(this, flow)
