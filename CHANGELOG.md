# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 1.2.0 - 2026-11-27

### Added

- **`KeyedRateLimiter`, a rate limiter per key.** `keyedRateLimiter<K>(name, maxKeys, idleTimeout, environment)`
  gives every key, such as a principal or the address of a client, a rate limiter of its own. Keys live in an
  `OrderedMap` of Kore, found by comparison and never by hash code; a key idle for `idleTimeout`, which is never
  shorter than the refresh period of the limit, is forgotten once the limiter is full, and a new key that finds it
  full of keys in use is refused with `RequestNotPermittedException`, so a flood of keys fails closed. Its telemetry
  is the counter `kontinuity.keyed_rate_limiter.calls` and the gauge `kontinuity.keyed_rate_limiter.keys`, without a
  time series per key.
- **A guarded transport for the metric exporter of kollect.** `Transport.guarded(policy)` sends every OTLP request
  through a policy, such as a circuit breaker and a rate limiter; a request a stage refuses becomes
  `Delivery.Throttled`, so the exporter keeps it and no point is lost.

## 1.1.0 - 2026-11-26

kontinuity is a direct dependency of the libraries that need resilience: the connect retry of Konnect, the response
cache of krypt-ocsp and the reconnections of kapi build on it.

### Changed

- **`SecureEntropy` draws from `SecureEntropy` of Kore.** The default entropy reads `SecureRandom` on the JVM and
  `crypto.getRandomValues` on Wasm instead of the random bits of version 4 UUIDs.
- **The backoffs compute their delays with Kore 2.5.0.** The exponential growth and the full, equal, proportional and
  decorrelated jitter are the shared arithmetic of Kore; the delays are unchanged.
- **Events reach the bus through the `Emitter` of konfetti 1.1.0.** A bus that declares the topic of an instance for
  another event type no longer fails the instance; its events then reach the flow of the instance only.
- **Built with Kreate 5.5.2 and Gradle 9.9.2 on Kore 2.5.0, konfetti 1.1.0 and kollect 1.1.0**, with the version
  floors of the build tools raised.

### Deprecated

- **`Guard` and `guard()` of `com.davils.kontinuity.core.telemetry`.** Every module uses the guard of Kore,
  `com.davils.kore.core.concurrency.guard`; the deprecated declarations delegate to it and go away in 2.0.0.

## 1.0.0 - 2026-11-21

The first version of kontinuity: the resilience library of Davils as an open source library under the Apache License
2.0, for Kotlin on the JVM and on `wasmJs`, built with Kreate 5.4.1 on Kore 2.3.2, konfetti 1.0.0 and kollect 1.0.0 and
published to `https://repo.davils.com`. The code comes from the resilience library of an internal Davils project, is
hardened for critical infrastructure on the way and covers the feature set of resilience4j.

### Added

- **The build** of kontinuity with Kreate 5.4.1: one module per component and a bill of materials, one convention
  plugin per concern, strict dependency locking and verification, Detekt with every rule over main and test sources,
  Kover at 97 %, the API and semver check, Trivy, the standards audit and the assurance gate.
- **The core**: instance names checked by a grammar, stages with an operator form, `attempt`, decorators and a `Flow`
  operator, `CallRejectedException` as the base of every refusal, failure and result filters chosen by reified type,
  fallbacks chosen by the type of the failure, fixed and exponential backoffs with full, equal, decorrelated and
  proportional jitter, registries with default and named configurations that inherit from each other, and the
  environment that connects every instance to time, randomness, konfetti events, kollect meters, a tracer and the
  caller's scope.
- **The circuit breaker** with the closed, open, half-open, disabled, forced-open and metrics-only states,
  count-based and time-based windows, failure and slow-call rates, a growing wait in the open state, an automatic
  transition on the caller's scope, a bounded half-open state, opening failures and results, manual permits, health
  indicators and blocking and `CompletionStage` interop on the JVM.
- **The retry** with bounded attempts, backoffs, interval functions by failure and result, retries on results, a
  failure after exhausted attempts, a disposal hook for discarded results and a retry budget shared between retries.
- **The rate limiter** with fixed-window, sliding-window and token-bucket algorithms, bounded waits and queues,
  reservations, runtime changes of the limit and the timeout, and draining on failures and results.
- **The bulkheads**: a fair semaphore bulkhead that can be resized at runtime and a dispatcher bulkhead with a bounded
  queue on a dispatcher of the caller, the counterpart of the thread pool bulkhead of resilience4j.
- **The time limiter** with hard and soft timeouts, bounded detached work and future and blocking interop on the JVM.
- **The cache** with LRU, LFU and FIFO eviction in constant time, size and weight bounds, expiry after write, after
  access and per entry, single-flight loading, stale values on failure and a reaper on the caller's scope.
- **The hedge** with fixed and latency-percentile delays and a bounded number of hedged attempts.
- **The policy** that composes every component in a fixed, documented order, with fallbacks and caches around it,
  tracing, events and meters.
- **Test support** with test environments on virtual time, fault plans, counting and refusing stages and event logs.

### Security

- Every count, duration, queue and cache is bounded by limits with hard ceilings that no configuration exceeds.
- Every call is counted exactly once, and the rejection of one component is never a failure of another.
- Predicates, fallbacks, interval functions, loaders, weighers and the entropy run contained.
- Events, meters and messages show instance names, numbers and the simple type names of failures only; cache keys
  only when a cache opts in.
- Jitter and hedging use secure randomness by default.
