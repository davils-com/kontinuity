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

internal class RecencyOrder<K : Any, V : Any>(private val isAccessOrdered: Boolean) : EvictionOrder<K, V> {
    private var nodes: NodeList<K, V> = NodeList()

    override fun add(node: Node<K, V>) {
        nodes.append(node)
    }

    override fun accessed(node: Node<K, V>) {
        if (isAccessOrdered) rewritten(node)
    }

    override fun rewritten(node: Node<K, V>) {
        nodes.unlink(node)
        nodes.append(node)
    }

    override fun remove(node: Node<K, V>) {
        nodes.unlink(node)
    }

    override fun first(): Node<K, V>? = nodes.head

    override fun after(node: Node<K, V>): Node<K, V>? = node.next

    override fun clear() {
        nodes = NodeList()
    }
}
