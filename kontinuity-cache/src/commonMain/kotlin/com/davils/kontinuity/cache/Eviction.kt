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

/**
 * Which entry a full [Cache] gives up to make room for a new one.
 *
 * Every policy chooses its victim, records an access and records a write in constant time, whatever the number of
 * entries: the cache never sorts or scans its entries to evict one, and it evicts while it holds its lock for no
 * longer than the removal of the victims takes.
 *
 * @since 1.0.0
 */
public enum class Eviction {
    /**
     * Least recently used: the entry read or written longest ago goes first. The entries form a doubly linked list
     * in the order of their last use; a read or a write moves an entry to its end.
     *
     * @since 1.0.0
     */
    Lru,

    /**
     * Least frequently used: the entry read and written the fewest times goes first and, among entries used equally
     * often, the one that reached that count first. The entries live in buckets of equal use counts, so that a use
     * moves an entry to the next bucket in constant time. A new entry starts with a count of one and is never the
     * victim of its own write.
     *
     * @since 1.0.0
     */
    Lfu,

    /**
     * First in, first out: the entry written longest ago goes first; reads do not change the order, a write of a
     * new value moves the entry to the end.
     *
     * @since 1.0.0
     */
    Fifo,
}
