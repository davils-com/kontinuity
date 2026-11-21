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

internal class FrequencyOrder<K : Any, V : Any> : EvictionOrder<K, V> {
    private var lowest: Bucket<K, V>? = null

    override fun add(node: Node<K, V>) {
        val first = lowest
        val target = first?.takeIf { bucket -> bucket.frequency == 1L } ?: bucketAfter(previous = null, frequency = 1L)
        place(node, target)
    }

    override fun accessed(node: Node<K, V>) {
        val current = node.bucket ?: return
        val frequency = current.frequency + 1L
        val following = current.next
        val target = following?.takeIf { bucket -> bucket.frequency == frequency } ?: bucketAfter(current, frequency)
        leave(node, current)
        place(node, target)
    }

    override fun rewritten(node: Node<K, V>) {
        accessed(node)
    }

    override fun remove(node: Node<K, V>) {
        val current = node.bucket ?: return
        leave(node, current)
    }

    override fun first(): Node<K, V>? = lowest?.head

    override fun after(node: Node<K, V>): Node<K, V>? = node.next ?: nextBucketHead(node)

    private fun nextBucketHead(node: Node<K, V>): Node<K, V>? {
        val following = node.bucket?.next ?: return null
        return following.head
    }

    override fun clear() {
        lowest = null
    }

    private fun place(node: Node<K, V>, bucket: Bucket<K, V>) {
        bucket.admit(node)
    }

    private fun leave(node: Node<K, V>, bucket: Bucket<K, V>) {
        if (bucket.release(node)) unlink(bucket)
    }

    private fun bucketAfter(previous: Bucket<K, V>?, frequency: Long): Bucket<K, V> {
        val bucket = Bucket<K, V>(frequency)
        val following = previous?.next ?: lowest.takeIf { previous == null }
        bucket.previous = previous
        bucket.next = following
        following?.previous = bucket
        if (previous == null) lowest = bucket
        previous?.next = bucket
        return bucket
    }

    private fun unlink(bucket: Bucket<K, V>) {
        val before = bucket.previous
        val after = bucket.next
        if (before == null) lowest = after
        before?.next = after
        after?.previous = before
    }
}
