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

package com.davils.kontinuity.policy

import com.davils.kollect.core.Payload
import com.davils.kollect.core.export.Delivery
import com.davils.kollect.core.health.ProbeRenderer
import com.davils.kontinuity.circuitbreaker.CircuitState
import com.davils.kontinuity.circuitbreaker.circuitBreaker
import com.davils.kontinuity.policy.fixture.CountingTransport
import com.davils.kontinuity.ratelimiter.rateLimiter
import com.davils.kontinuity.testing.testEnvironment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val RECEIVER: String = "receiver"

private val payload: Payload = ProbeRenderer().live().body

@OptIn(ExperimentalKotest::class)
class GuardedTransportTest : FunSpec({
    coroutineTestScope = true

    test("sends through the policy and hands back what the receiver answered") {
        val transport = CountingTransport { Delivery.Accepted() }
        val policy = resilience(RECEIVER) { circuitBreaker(circuitBreaker(RECEIVER)) }

        val guarded = transport.guarded(policy)

        guarded.send(payload).shouldBeInstanceOf<Delivery.Accepted>()

        transport.sent shouldBe 1
        policy.snapshot().successful shouldBe 1L
    }

    test("throttles a request an open circuit breaker refuses without sending it") {
        val transport = CountingTransport { Delivery.Accepted() }
        val breaker = circuitBreaker(RECEIVER)
        breaker.transitionTo(CircuitState.ForcedOpen)
        val policy = resilience(RECEIVER) { circuitBreaker(breaker) }

        val delivery = transport.guarded(policy).send(payload)

        val throttled = delivery.shouldBeInstanceOf<Delivery.Throttled>()
        throttled.retryAfter shouldBe null
        transport.sent shouldBe 0
    }

    test("throttles a request over the rate until the limiter frees its permit") {
        val transport = CountingTransport { Delivery.Accepted() }
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        val limiter = rateLimiter(RECEIVER, environment) {
            limitForPeriod = 1
            limitRefreshPeriod = 1.seconds
            timeout = Duration.ZERO
        }
        val guarded = transport.guarded(resilience(RECEIVER, environment) { rateLimiter(limiter) })
        guarded.send(payload)

        val delivery = guarded.send(payload)

        val throttled = delivery.shouldBeInstanceOf<Delivery.Throttled>()
        throttled.retryAfter shouldBe 1.seconds
        transport.sent shouldBe 1
    }

    test("lets a failure of the transport reach the exporter") {
        val transport = CountingTransport { error("down") }
        val policy = resilience(RECEIVER) { circuitBreaker(circuitBreaker(RECEIVER)) }

        shouldThrow<IllegalStateException> { transport.guarded(policy).send(payload) }

        policy.snapshot().failed shouldBe 1L
    }
})
