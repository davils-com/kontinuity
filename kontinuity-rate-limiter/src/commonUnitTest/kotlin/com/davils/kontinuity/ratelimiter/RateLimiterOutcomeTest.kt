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

import com.davils.kontinuity.core.CallFailure
import com.davils.kontinuity.core.attempt
import com.davils.kontinuity.core.invoke
import com.davils.kontinuity.ratelimiter.fixture.LIMIT
import com.davils.kontinuity.ratelimiter.fixture.PERIOD
import com.davils.kontinuity.ratelimiter.fixture.quietly
import com.davils.kontinuity.ratelimiter.fixture.smallLimiter
import com.davils.kontinuity.testing.TestRejection
import com.davils.kontinuity.testing.testEnvironment
import com.davils.kore.core.outcome.Outcome
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private const val OK: Int = 200
private const val TOO_MANY: Int = 429
private const val BROKEN: String = "broken rule"
private const val QUOTA: String = "quota exceeded"

@OptIn(ExperimentalKotest::class)
class RateLimiterOutcomeTest : FunSpec({
    coroutineTestScope = true

    test("a call takes its permits and returns its result") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource))
        limiter.execute { OK } shouldBe OK
        limiter { OK } shouldBe OK
        limiter.execute(permits = 1) { OK } shouldBe OK
        limiter.attempt { OK }.shouldBeInstanceOf<Outcome.Success<Int>>()
        limiter.snapshot().permitsGranted shouldBe (LIMIT + 1).toLong()
    }

    test("a failure the drain filter records drains the period") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            drainOnFailures { record<UnsupportedOperationException>() }
        }
        shouldThrow<IllegalStateException> { limiter.execute { error(BROKEN) } }
        limiter.availablePermissions shouldBe LIMIT - 1
        shouldThrow<UnsupportedOperationException> { limiter.execute { throw UnsupportedOperationException(QUOTA) } }
        limiter.availablePermissions shouldBe 0
        limiter.snapshot().acquired shouldBe 2L
    }

    test("a rejection of another component never drains") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            drainOnFailures { }
        }
        shouldThrow<TestRejection> { limiter.execute { throw TestRejection() } }
        limiter.availablePermissions shouldBe LIMIT - 1
    }

    test("a result the drain filter accepts drains the period") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            drainOnResults { failIf<Int> { status -> status == TOO_MANY } }
        }
        limiter.execute { OK } shouldBe OK
        limiter.availablePermissions shouldBe LIMIT - 1
        limiter.execute { TOO_MANY } shouldBe TOO_MANY
        limiter.availablePermissions shouldBe 0
    }

    test("a drain rule that throws fails the call and drains only if the failure filter records it") {
        val kept = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            drainOnResults { failIf<Int> { _ -> error(BROKEN) } }
        }
        shouldThrow<IllegalStateException> { kept.execute { OK } }.message shouldBe BROKEN
        kept.availablePermissions shouldBe LIMIT - 1
        val drained = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) {
            drainOnResults { failIf<Int> { _ -> error(BROKEN) } }
            drainOnFailures { record<IllegalStateException>() }
        }
        shouldThrow<IllegalStateException> { drained.execute { OK } }
        drained.availablePermissions shouldBe 0
    }

    test("a refused call never runs") {
        val limiter = smallLimiter(testEnvironment(testCoroutineScheduler.timeSource)) { timeout = PERIOD / 2 }
        var runs = 0
        repeat(LIMIT + 1) { quietly { limiter.execute { runs += 1 } } }
        runs shouldBe LIMIT
        val refused = limiter.attempt { runs += 1 }
        val failure = (refused as Outcome.Failure).failure
        failure.shouldBeInstanceOf<CallFailure.Rejected>()
        failure.cause.shouldBeInstanceOf<RequestNotPermittedException>()
    }
})
