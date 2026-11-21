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

import com.davils.kollect.core.meterRegistry
import com.davils.kontinuity.bulkhead.bulkhead
import com.davils.kontinuity.bulkhead.dispatcherBulkhead
import com.davils.kontinuity.cache.cache
import com.davils.kontinuity.circuitbreaker.CallNotPermittedException
import com.davils.kontinuity.circuitbreaker.CircuitState
import com.davils.kontinuity.circuitbreaker.circuitBreaker
import com.davils.kontinuity.circuitbreaker.countBased
import com.davils.kontinuity.core.CallTimeoutException
import com.davils.kontinuity.core.InstanceName
import com.davils.kontinuity.core.ResilienceKind
import com.davils.kontinuity.core.backoff.fixed
import com.davils.kontinuity.core.fallback.fallback
import com.davils.kontinuity.hedge.fixedDelay
import com.davils.kontinuity.hedge.hedge
import com.davils.kontinuity.policy.fixture.ANSWER
import com.davils.kontinuity.policy.fixture.RecordingTracer
import com.davils.kontinuity.policy.fixture.failing
import com.davils.kontinuity.policy.fixture.quietly
import com.davils.kontinuity.ratelimiter.rateLimiter
import com.davils.kontinuity.retry.retry
import com.davils.kontinuity.testing.CountingStage
import com.davils.kontinuity.testing.RefusingStage
import com.davils.kontinuity.testing.TestRejection
import com.davils.kontinuity.testing.faults
import com.davils.kontinuity.testing.recordEvents
import com.davils.kontinuity.testing.testEnvironment
import com.davils.kontinuity.timelimiter.timeLimiter
import com.davils.kore.core.validation.report.ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val CHECKOUT: String = "checkout"
private const val ATTEMPTS: Int = 3
private const val WINDOW: Int = 4
private const val FALLBACK: Int = -1
private const val KEY: String = "order"
private const val SEARCH: String = "search"
private const val SLOW: String = "slow"
private const val POOL: String = "pool"
private const val SEATS: String = "seats"
private const val RATE: String = "rate"
private const val PAYMENTS: String = "payments"
private const val AGAIN: String = "again"

@OptIn(ExperimentalKotest::class)
class PolicyTest : FunSpec({
    coroutineTestScope = true

    test("a policy nests its components in a fixed order whatever the order of declaration") {
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        val inner = CountingStage()
        val dispatcher = StandardTestDispatcher(testCoroutineScheduler)
        val policy = resilience(CHECKOUT, environment) {
            stage(inner)
            hedge(hedge(SEARCH, environment))
            timeLimiter(timeLimiter(SLOW, environment))
            dispatcherBulkhead(
                dispatcherBulkhead(POOL, dispatcher, environment),
            )
            bulkhead(bulkhead(SEATS, environment))
            rateLimiter(rateLimiter(RATE, environment))
            circuitBreaker(circuitBreaker(PAYMENTS, environment))
            retry(retry(AGAIN, environment))
        }
        policy.stages.map { stage -> stage.kind } shouldBe listOf(
            ResilienceKind.Retry,
            ResilienceKind.CircuitBreaker,
            ResilienceKind.RateLimiter,
            ResilienceKind.Bulkhead,
            ResilienceKind.DispatcherBulkhead,
            ResilienceKind.TimeLimiter,
            ResilienceKind.Hedge,
            ResilienceKind.Policy,
        )
        policy.execute { ANSWER } shouldBe ANSWER
        inner.calls shouldBe 1
        policy.kind shouldBe ResilienceKind.Policy
        policy.toString() shouldBe "Policy(checkout, retry again -> circuit-breaker payments -> rate-limiter rate -> " +
            "bulkhead seats -> dispatcher-bulkhead pool -> time-limiter slow -> hedge search -> policy counting)"
    }

    test("a retry repeats the whole chain and the breaker counts every attempt") {
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        val breaker = circuitBreaker(PAYMENTS, environment)
        val policy = resilience(CHECKOUT, environment) {
            retry(retry(AGAIN, environment) { backoff = fixed(1.seconds) })
            circuitBreaker(breaker)
        }
        val plan = faults { failFirst(ATTEMPTS - 1) }
        policy.execute { plan { ANSWER } } shouldBe ANSWER
        breaker.snapshot().totals.failed shouldBe (ATTEMPTS - 1).toLong()
        breaker.snapshot().totals.successful shouldBe 1L
        policy.snapshot().successful shouldBe 1L
    }

    test("a refusal of the bulkhead is a rejection of the policy and no failure of the breaker") {
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        val breaker = circuitBreaker(PAYMENTS, environment) {
            slidingWindow = countBased(WINDOW)
            minimumNumberOfCalls = WINDOW
        }
        val policy = resilience(CHECKOUT, environment) {
            circuitBreaker(breaker)
            stage(RefusingStage())
        }
        val log = recordEvents(policy.events)
        repeat(WINDOW) { shouldThrow<TestRejection> { policy.execute { ANSWER } } }
        breaker.state shouldBe CircuitState.Closed
        policy.snapshot().rejected shouldBe WINDOW.toLong()
        testCoroutineScheduler.runCurrent()
        log.stop()
        log.events.first().toString() shouldBe "Rejected(checkout, bulkhead refusing)"
    }

    test("the time limiter bounds the call but not the wait for a bulkhead slot") {
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        val policy = resilience(CHECKOUT, environment) {
            bulkhead(
                bulkhead(SEATS, environment) {
                    maxConcurrentCalls = 1
                    maxWaitDuration = 10.seconds
                },
            )
            timeLimiter(timeLimiter(SLOW, environment) { timeout = 2.seconds })
        }
        val first = async { policy.execute { delay(1.seconds) } }
        yield()
        policy.execute { delay(1.seconds) }
        first.await()
        shouldThrow<CallTimeoutException> { policy.execute { delay(3.seconds) } }
        policy.snapshot().failed shouldBe 1L
    }

    test("the breaker does not count the wait for the rate limiter as slowness") {
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        val breaker = circuitBreaker(PAYMENTS, environment) { slowCallDurationThreshold = 1.seconds }
        val policy = resilience(CHECKOUT, environment) {
            circuitBreaker(breaker)
            rateLimiter(
                rateLimiter(RATE, environment) {
                    limitForPeriod = 1
                    limitRefreshPeriod = 5.seconds
                    timeout = 10.seconds
                },
            )
        }
        policy.execute { ANSWER }
        policy.execute { ANSWER }
        breaker.snapshot().window.slowCalls shouldBe 0
    }

    test("a fallback goes around the policy and handles its rejections") {
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        val breaker = circuitBreaker(PAYMENTS, environment)
        breaker.transitionTo(CircuitState.ForcedOpen)
        val policy = resilience(CHECKOUT, environment) { circuitBreaker(breaker) }
        val answer = fallback<Int>("answer") { on<CallNotPermittedException> { FALLBACK } }
        policy.execute(answer) { ANSWER } shouldBe FALLBACK
    }

    test("a cached policy loads a key once through the policy") {
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        val inner = CountingStage()
        val cached = resilience(
            CHECKOUT,
            environment,
        ) { stage(inner) }.cached(cache<String, Int>("orders", environment))
        cached.execute(KEY) { _ -> ANSWER } shouldBe ANSWER
        cached.execute(KEY) { _ -> failing() } shouldBe ANSWER
        inner.calls shouldBe 1
        cached.invalidate(KEY) shouldBe ANSWER
        cached.toString() shouldBe "CachedPolicy(checkout, orders)"
    }

    test("every call is counted once, reported and traced") {
        val tracer = RecordingTracer()
        val registry = meterRegistry()
        val environment = testEnvironment(testCoroutineScheduler.timeSource) {
            this.tracer = tracer
            meters(registry)
        }
        val policy = resilience(CHECKOUT, environment) { stage(CountingStage()) }
        val log = recordEvents(policy.events)
        policy.execute { ANSWER }
        quietly { policy.execute { failing() } }
        val call = async { policy.execute { delay(1.seconds) } }
        yield()
        call.cancel()
        shouldThrow<CancellationException> { call.await() }
        testCoroutineScheduler.runCurrent()
        log.stop()
        val snapshot = policy.snapshot()
        snapshot.calls shouldBe ATTEMPTS.toLong()
        snapshot.toString() shouldBe "PolicySnapshot(successful=1, failed=1, rejected=0, cancelled=1)"
        val shown = log.events.map(PolicyEvent::toString)
        shown shouldContain "Succeeded(checkout, 0s)"
        shown shouldContain "Failed(checkout, 0s, IllegalStateException)"
        shown shouldContain "Cancelled(checkout)"
        tracer.spans shouldContain "kontinuity.policy kontinuity.policy.name=checkout ok"
        log.only<PolicyEvent.Succeeded>().single().kind shouldBe ResilienceKind.Policy
    }

    test("a flow through a policy is one call") {
        val policy = resilience(CHECKOUT, testEnvironment(testCoroutineScheduler.timeSource)) {
            stage(CountingStage())
        }
        val protected = flowOf(ANSWER, FALLBACK).resilience(policy)
        protected.toList() shouldBe listOf(ANSWER, FALLBACK)
        policy.snapshot().successful shouldBe 1L
    }

    test("an invalid policy is refused with every problem") {
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        shouldThrow<ValidationException> { resilience(CHECKOUT, environment) {} }
        val stage = CountingStage()
        val refusal = shouldThrow<ValidationException> {
            resilience(CHECKOUT, environment) {
                retry(retry(AGAIN, environment))
                retry(retry("other", environment))
                stage(stage)
                stage(stage)
                rateLimiter(rateLimiter(RATE, environment) { limitForPeriod = 1 }, permits = 2)
            }
        }
        refusal.report.violations.size shouldBe ATTEMPTS
        resilience(CHECKOUT, environment) {
            rateLimiter(rateLimiter(RATE, environment) { limitForPeriod = 2 }, permits = 2)
        }.stages.size shouldBe 1
    }

    test("a hedged policy takes the first successful copy") {
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        val policy = resilience(CHECKOUT, environment) {
            hedge(hedge(SEARCH, environment) { delay = fixedDelay(100.milliseconds) })
        }
        val plan = faults { latency = 1.seconds }
        policy.execute { plan { ANSWER } } shouldBe ANSWER
        plan.calls shouldBe 2
    }

    test("a builder cannot change after it was evaluated") {
        val environment = testEnvironment(testCoroutineScheduler.timeSource)
        val builder = PolicyBuilder(
            InstanceName.of(CHECKOUT),
            environment,
        )
        builder.stage(CountingStage())
        builder.build()
        shouldThrow<IllegalStateException> { builder.stage(CountingStage()) }
        shouldThrow<IllegalStateException> { builder.retry(retry(AGAIN, environment)) }
    }
})
