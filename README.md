# kontinuity

kontinuity is the resilience library of Davils: circuit breakers, retries, rate limiters, bulkheads, time limiters,
caches, hedges and fallbacks, composed by one policy DSL, for Kotlin on the JVM and on WebAssembly (`wasmJs`). It
covers the feature set of resilience4j with suspending calls, `Flow` operators and a Kotlin DSL, reports through events
on [konfetti](https://github.com/davils-com/konfetti) topics and meters and health in
[kollect](https://github.com/davils-com/kollect), and is built on [Kore](https://github.com/davils-com/kore) for use in
critical infrastructure. It is published under the [Apache License 2.0](LICENSE).

| Module | Artifact | What it holds |
|---|---|---|
| `kontinuity-core` | `com.davils:kontinuity-core` | Names, stages, outcomes, rejections, failure and result filters, fallbacks, backoffs, registries and the environment |
| `kontinuity-circuit-breaker` | `com.davils:kontinuity-circuit-breaker` | The circuit breaker with all six states of resilience4j |
| `kontinuity-retry` | `com.davils:kontinuity-retry` | The retry with backoffs, interval functions and a retry budget |
| `kontinuity-rate-limiter` | `com.davils:kontinuity-rate-limiter` | The rate limiter with fixed-window, sliding-window and token-bucket limits |
| `kontinuity-bulkhead` | `com.davils:kontinuity-bulkhead` | The semaphore bulkhead and the dispatcher bulkhead |
| `kontinuity-time-limiter` | `com.davils:kontinuity-time-limiter` | The time limiter with hard and soft timeouts |
| `kontinuity-cache` | `com.davils:kontinuity-cache` | The bounded in-memory cache with single-flight loading |
| `kontinuity-hedge` | `com.davils:kontinuity-hedge` | The hedge for slow calls |
| `kontinuity-policy` | `com.davils:kontinuity-policy` | The `resilience { }` DSL that composes every component |
| `kontinuity-testing` | `com.davils:kontinuity-testing` | Test environments, fault plans and event logs |
| `kontinuity-bom` | `com.davils:kontinuity-bom` | The bill of materials of every module |

kontinuity is a Kotlin Multiplatform library for `jvm` and `wasmJs`, written for Kotlin 2.4 or newer. On the JVM it
requires JDK 25; every module is a named Java module, such as `com.davils.kontinuity.circuitbreaker`. On Wasm it is
tested under Node.js.

## Installation

kontinuity is published to the Davils repository, not to Maven Central. Declare it for the `com.davils` group only:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository { maven("https://repo.davils.com/releases") }
            filter { includeGroupAndSubgroups("com.davils") }
        }
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation(platform("com.davils:kontinuity-bom:1.0.0"))
    implementation("com.davils:kontinuity-policy")
    testImplementation("com.davils:kontinuity-testing")
}
```

A Maven build names the JVM artifacts directly, such as `com.davils:kontinuity-policy-jvm`.

## A short tour

```kotlin
val environment = environment {
    events(bus)
    meters(meterRegistry)
    scope(applicationScope)
}

val breaker = circuitBreaker("payments", environment) {
    failureRateThreshold = 30.0
    slidingWindow = countBased(50)
    failures { ignore<InvalidCardException>() }
}

val payments = resilience("payments.charge", environment) {
    retry(retry("payments.retry", environment) {
        maxAttempts = 3
        backoff = exponential(initial = 200.milliseconds).jittered(Jitter.Full)
    })
    circuitBreaker(breaker)
    bulkhead(bulkhead("payments.seats", environment) { maxConcurrentCalls = 32 })
    timeLimiter(timeLimiter("payments.timeout", environment) { timeout = 2.seconds })
}

val receipt = payments.execute { paymentClient.charge(order) }
```

Every component is a `Stage` and works on its own, too: `breaker { call() }`, `retry.decorate { call() }` or
`flow.circuitBreaker(breaker)`. A refusal of a component throws a `CallRejectedException`; no component counts the
refusal of another as a failure, so a full bulkhead never opens a circuit breaker. Registries such as
`circuitBreakerRegistry { defaults { }; config("strict") { } }` hold named instances with configurations that inherit
from each other.

## Security

kontinuity assumes that the failures, results and timing of protected calls, the names of instances and the keys of a
cache may come from an attacker. Every structure is bounded by limits with hard ceilings, every wait has a timeout,
every call is counted exactly once, foreign code runs contained, and events and messages show only names, numbers and
type names. [SECURITY.md](SECURITY.md) describes the threat model and how to report a vulnerability.

## Building

```bash
./gradlew check
```

It needs JDK 25 and network access. [AGENTS.md](AGENTS.md) holds the rules every change follows and
[CONTRIBUTING.md](CONTRIBUTING.md) how a change gets in.

## License

Copyright 2026 Davils. Licensed under the [Apache License, Version 2.0](LICENSE).
