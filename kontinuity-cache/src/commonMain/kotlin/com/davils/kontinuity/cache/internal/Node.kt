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

package com.davils.kontinuity.cache.internal

import kotlin.time.ComparableTimeMark

internal class Node<K : Any, V : Any>(val key: K, var value: V, var weight: Int) {
    var writeDeadline: ComparableTimeMark? = null
    var accessDeadline: ComparableTimeMark? = null
    var previous: Node<K, V>? = null
    var next: Node<K, V>? = null
    var bucket: Bucket<K, V>? = null

    fun deadline(): ComparableTimeMark? {
        val write = writeDeadline
        val access = accessDeadline
        if (write == null || access == null) return write ?: access
        return minOf(a = write, b = access)
    }
}
