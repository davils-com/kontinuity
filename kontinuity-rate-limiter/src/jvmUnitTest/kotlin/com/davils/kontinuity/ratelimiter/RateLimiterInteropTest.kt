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

package com.davils.kontinuity.ratelimiter

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

private const val OK: Int = 200
private const val TOO_MANY: Int = 429
private const val BROKEN: String = "broken"
private const val POLL_MILLIS: Long = 5L
private const val JOIN_MILLIS: Long = 10_000L

class RateLimiterInteropTest : FunSpec({
    test("a blocking call takes its permits and waits for the next period") {
        val limiter = rateLimiter("interop") {
            limitForPeriod = 1
            limitRefreshPeriod = 50.milliseconds
        }
        limiter.executeBlocking { OK } shouldBe OK
        val started = System.nanoTime()
        limiter.executeBlocking(permits = 1) { OK } shouldBe OK
        (System.nanoTime() - started > 0L) shouldBe true
        shouldThrow<IllegalArgumentException> { limiter.executeBlocking(permits = 2) { OK } }
        limiter.snapshot().acquired shouldBe 2L
    }

    test("a blocking call drains on a recorded failure or result") {
        val limiter = rateLimiter("drains") {
            limitForPeriod = 2
            limitRefreshPeriod = 1.hours
            drainOnFailures { record<UnsupportedOperationException>() }
            drainOnResults { failIf<Int> { status -> status == TOO_MANY } }
        }
        shouldThrow<IllegalStateException> { limiter.executeBlocking { error(BROKEN) } }
        limiter.availablePermissions shouldBe 1
        limiter.executeBlocking { TOO_MANY } shouldBe TOO_MANY
        limiter.availablePermissions shouldBe 0
        val other = rateLimiter("drains.failure") {
            limitForPeriod = 2
            limitRefreshPeriod = 1.hours
            drainOnFailures { record<UnsupportedOperationException>() }
        }
        shouldThrow<UnsupportedOperationException> {
            other.executeBlocking { throw UnsupportedOperationException(BROKEN) }
        }
        other.availablePermissions shouldBe 0
        shouldThrow<RequestNotPermittedException> { other.acquireBlocking(permits = 1, timeout = 0.milliseconds) }
    }

    test("an interrupt gives the reservation back and keeps the flag") {
        val limiter = rateLimiter("interrupted") {
            limitForPeriod = 1
            limitRefreshPeriod = 1.hours
            timeout = 1.hours
        }
        limiter.acquireBlocking()
        val failure = AtomicReference<Throwable>()
        val flag = AtomicReference<Boolean>()
        val waiter = Thread {
            failure.set(runCatching { limiter.acquireBlocking() }.exceptionOrNull())
            flag.set(Thread.currentThread().isInterrupted)
        }
        waiter.start()
        while (limiter.waitingCallers == 0) delay(POLL_MILLIS)
        waiter.interrupt()
        waiter.join(JOIN_MILLIS)
        failure.get().shouldBeInstanceOf<IllegalStateException>()
        failure.get().cause.shouldBeInstanceOf<InterruptedException>()
        flag.get() shouldBe true
        limiter.waitingCallers shouldBe 0
        limiter.snapshot().cancelled shouldBe 1L
        (limiter.reservePermission() != null) shouldBe true
    }
})
