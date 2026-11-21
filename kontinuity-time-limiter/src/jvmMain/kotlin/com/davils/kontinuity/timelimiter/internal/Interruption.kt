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

package com.davils.kontinuity.timelimiter.internal

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class Interruption {
    private val lock = ReentrantLock()
    private var running: Thread? = null
    private var isCancelled = false
    private var isInterrupted = false

    fun <T> run(block: () -> T): T {
        enter()
        try {
            return block()
        } finally {
            exit()
        }
    }

    fun interrupt() {
        lock.withLock {
            isCancelled = true
            running?.let(::interrupted)
        }
    }

    private fun enter() {
        val current = Thread.currentThread()
        lock.withLock {
            running = current
            if (isCancelled) interrupted(current)
        }
    }

    private fun exit() {
        lock.withLock {
            running = null
            if (isInterrupted) Thread.interrupted()
        }
    }

    private fun interrupted(thread: Thread) {
        isInterrupted = true
        thread.interrupt()
    }
}
