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

internal class WaitQueue {
    private var head: Waiter? = null
    private var tail: Waiter? = null

    var size: Int = 0
        private set

    fun add(waiter: Waiter) {
        val last = tail
        waiter.previous = last
        waiter.next = null
        if (last == null) head = waiter
        last?.next = waiter
        tail = waiter
        size += 1
    }

    fun poll(): Waiter? {
        val first = head ?: return null
        remove(first)
        return first
    }

    fun remove(waiter: Waiter) {
        val before = waiter.previous
        val after = waiter.next
        if (before == null) head = after
        before?.next = after
        if (after == null) tail = before
        after?.previous = before
        waiter.previous = null
        waiter.next = null
        size -= 1
    }
}
