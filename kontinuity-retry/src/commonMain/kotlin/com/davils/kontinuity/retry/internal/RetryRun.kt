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

@file:OptIn(DavilsInternal::class)

package com.davils.kontinuity.retry.internal

import com.davils.konfetti.core.bus.Entropy
import com.davils.kontinuity.core.backoff.Backoff
import com.davils.kontinuity.core.backoff.delayBefore
import com.davils.kontinuity.core.filter.FailureVerdict
import com.davils.kontinuity.retry.RetriesExhaustedException
import com.davils.kontinuity.retry.RetryConfig
import com.davils.kore.core.annotation.DavilsInternal
import com.davils.kore.core.failure.contained
import kotlin.time.Duration

private const val RESULT: String = "result"
private const val UNNAMED_FAILURE: String = "Throwable"
private const val UNNAMED_RESULT: String = "Any"
private const val NULL_RESULT: String = "null"

internal class RetryRun(
    private val config: RetryConfig,
    private val entropy: Entropy,
    private val reporter: Reporter,
) {
    private var attempt: Int = 1
    private var previous: Duration = Duration.ZERO
    private var isConcluded: Boolean = false

    init {
        config.budget?.deposit()
    }

    fun decide(outcome: Result<Any?>): Decision = outcome.fold(onSuccess = ::judged, onFailure = ::failed)

    fun settleOpen(isCancelled: Boolean) {
        if (isConcluded) return
        isConcluded = true
        reporter.abandoned(attempts = attempt, isCancelled = isCancelled)
    }

    private fun failed(failure: Throwable): Decision {
        val type = failure::class.simpleName ?: UNNAMED_FAILURE
        val verdict = config.failures.verdictOf(failure)
        if (verdict == FailureVerdict.Recorded) return retriedOrFailed(failure, type)
        conclude()
        reporter.ignored(attempt, type)
        return Decision.Fail(failure)
    }

    private fun retriedOrFailed(failure: Throwable, type: String): Decision {
        if (permission() == Permission.Granted) return again(failure = failure, result = null, reasonType = type)
        conclude()
        reporter.exhausted(attempt, type)
        return Decision.Fail(failure)
    }

    private fun judged(result: Any?): Decision {
        val judgement = contained { config.results.isFailure(result) }
        val problem = judgement.exceptionOrNull() ?: return verdictOn(result, judgement.getOrThrow())
        discard(result)
        conclude()
        reporter.ignored(attempt, problem::class.simpleName ?: UNNAMED_FAILURE)
        return Decision.Fail(problem)
    }

    private fun verdictOn(result: Any?, isFailure: Boolean): Decision {
        if (isFailure) return retriedOrReturned(result)
        conclude()
        reporter.succeeded(attempt)
        return Decision.Return
    }

    private fun retriedOrReturned(result: Any?): Decision {
        val permission = permission()
        if (permission == Permission.Granted) {
            val decision = again(failure = null, result = result, reasonType = RESULT)
            discard(result)
            return decision
        }
        conclude()
        reporter.exhausted(attempt, RESULT)
        return givenUp(result, permission)
    }

    private fun givenUp(result: Any?, permission: Permission): Decision {
        val isThrown = permission == Permission.Exhausted && config.isFailingAfterMaxAttempts
        if (!isThrown) return Decision.Return
        discard(result)
        val exhaustion = RetriesExhaustedException(
            instance = reporter.name,
            attempts = attempt,
            resultType = typeOf(result),
        )
        return Decision.Fail(exhaustion)
    }

    private fun permission(): Permission {
        if (attempt >= config.maxAttempts) return Permission.Exhausted
        val isGranted = config.budget?.tryWithdraw() != false
        if (!isGranted) reporter.budgetDenied(attempt + 1)
        return Permission.Granted.takeIf { isGranted } ?: Permission.Denied
    }

    private fun again(failure: Throwable?, result: Any?, reasonType: String): Decision {
        val next = attempt + 1
        val delay = delayBefore(next, failure, result)
        reporter.retrying(attempt = next, delay = delay, reasonType = reasonType)
        attempt = next
        previous = delay
        return Decision.Again(delay)
    }

    private fun delayBefore(next: Int, failure: Throwable?, result: Any?): Duration {
        val interval = config.interval
        val chosen = interval?.let { choice -> contained { choice.delayBefore(next, failure, result) }.getOrNull() }
        val delay = chosen ?: config.backoff.delayBefore(attempt = next, previous = previous, entropy = entropy)
        return delay.coerceIn(minimumValue = Duration.ZERO, maximumValue = Backoff.HARD_MAXIMUM_DELAY)
    }

    private fun discard(result: Any?) {
        val hook = config.onDiscardedResult ?: return
        contained { hook(result) }
    }

    private fun conclude() {
        isConcluded = true
    }

    private fun typeOf(result: Any?): String {
        if (result == null) return NULL_RESULT
        return result::class.simpleName ?: UNNAMED_RESULT
    }
}
