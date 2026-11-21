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

package com.davils.kontinuity.retry.internal

import com.davils.kore.core.failure.rethrowIfFatal
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

private const val INTERRUPTED: String = "The thread of the retry was interrupted"

internal class BlockingRetry<T>(private val run: RetryRun, private val block: () -> T) {
    fun run(): T {
        try {
            return repeatUntilDone()
        } finally {
            run.settleOpen(isCancelled = Thread.currentThread().isInterrupted)
        }
    }

    private fun repeatUntilDone(): T {
        while (true) {
            val outcome = runCatching(block)
            outcome.exceptionOrNull()?.let(::stopOnFatal)
            when (val decision = run.decide(outcome)) {
                is Decision.Again -> pause(decision.delay)
                Decision.Return -> return outcome.getOrThrow()
                is Decision.Fail -> throw decision.failure
            }
        }
    }

    private fun stopOnFatal(failure: Throwable) {
        if (failure is InterruptedException) Thread.currentThread().interrupt()
        failure.rethrowIfFatal()
    }

    private fun pause(delay: Duration) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException(INTERRUPTED)
        try {
            TimeUnit.NANOSECONDS.sleep(delay.inWholeNanoseconds)
        } catch (interruption: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interruption
        }
    }
}
