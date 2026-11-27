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
import com.davils.kollect.core.export.Transport
import com.davils.kontinuity.core.CallRejectedException
import com.davils.kontinuity.ratelimiter.RequestNotPermittedException

/**
 * Returns a transport that sends every request of the kollect metric exporter through [policy], such as a circuit
 * breaker that stops sending to a receiver that is down and a rate limiter that bounds the requests per period.
 *
 * A stage of [policy] that refuses a request makes it [Delivery.Throttled]: with the time the rate limiter names when
 * it refused, without one otherwise. The exporter keeps the request and tries again after its backoff, so a refusal
 * never loses a point. What the transport throws reaches [policy] and is then treated by the exporter as
 * [Delivery.Retryable]; a policy that should also count answers such as [Delivery.Retryable] as failures declares it
 * through the result filters of its stages. Cancellation passes through.
 *
 * @receiver the transport that sends to the receiver.
 * @param policy the policy every request runs through.
 * @return the guarded transport.
 * @since 1.1.0
 */
public fun Transport.guarded(policy: Policy): Transport {
    val delegate = this
    return Transport { payload -> sent(delegate, policy, payload) }
}

private suspend fun sent(delegate: Transport, policy: Policy, payload: Payload): Delivery {
    try {
        return policy.execute { delegate.send(payload) }
    } catch (refused: CallRejectedException) {
        val retryAfter = (refused as? RequestNotPermittedException)?.availableIn
        return Delivery.Throttled(retryAfter)
    }
}
