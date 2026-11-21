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

internal class Bucket<K : Any, V : Any>(val frequency: Long) {
    private val nodes: NodeList<K, V> = NodeList()

    val head: Node<K, V>? get() = nodes.head
    var previous: Bucket<K, V>? = null
    var next: Bucket<K, V>? = null

    fun admit(node: Node<K, V>) {
        nodes.append(node)
        node.bucket = this
    }

    fun release(node: Node<K, V>): Boolean {
        nodes.unlink(node)
        node.bucket = null
        return nodes.isEmpty
    }
}
