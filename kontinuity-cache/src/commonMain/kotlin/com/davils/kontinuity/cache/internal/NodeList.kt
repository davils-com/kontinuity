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

internal class NodeList<K : Any, V : Any> {
    var head: Node<K, V>? = null
        private set
    private var tail: Node<K, V>? = null

    val isEmpty: Boolean get() = head == null

    fun append(node: Node<K, V>) {
        val last = tail
        node.previous = last
        node.next = null
        if (last == null) head = node
        last?.next = node
        tail = node
    }

    fun unlink(node: Node<K, V>) {
        val before = node.previous
        val after = node.next
        if (before == null) head = after
        before?.next = after
        if (after == null) tail = before
        after?.previous = before
        node.previous = null
        node.next = null
    }
}
