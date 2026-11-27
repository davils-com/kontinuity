# Security Policy

## Supported Versions

Security fixes are released for the latest minor version of the current major release.

| Version | Supported |
|---------|-----------|
| 1.2.x   | Yes       |
| < 1.2   | No        |

## Reporting a Vulnerability

**Do not open a public issue for a security problem.**

Report it privately through GitHub's
[private vulnerability reporting](https://github.com/davils-com/kontinuity/security/advisories/new),
or by email to development@davils.com.

Please include the affected kontinuity version, module and target (JVM or Wasm), a description of the impact, the
steps to reproduce it, ideally as a Kotlin test, and any mitigation you are aware of. We acknowledge a report within
three working days and agree on a disclosure date with you.

## Threat Model

kontinuity is a library: it runs in the process and with the privileges of the application that uses it. It protects
the application against failing and slow dependencies, so it is in the path of every protected call. It assumes that
an attacker controls:

- the **failures and results** of the protected calls, including the messages of exceptions and the content of
  results, and how long a call takes,
- the **rate and concurrency** of calls, up to a flood of calls from many coroutines and threads at once,
- **names** that come from configuration or requests, such as the names of instances and configurations of a registry,
- the **keys and values** of a cache, when the application caches by request data,
- the **failures** that predicates, interval functions, fallbacks, loaders, weighers and the entropy throw.

The properties that must hold are the availability of the application, the bounds of memory and waiting, the
correctness of every decision (a breaker that opens when it must, a limiter that never hands out more than its limit,
a bulkhead that never exceeds its concurrency), the exact accounting of every call, and the safety of `toString`
output, exception messages, events and meters.

kontinuity defends against this attacker with limits that are checked before any work and have hard ceilings, waits
and queues that are always bounded, names that follow a grammar checked by a `NameRule` of Kore, cause chains walked
within a bound and safe against cycles, containment of every piece of foreign code, events that show only names, numbers
and the simple type names of failures, and cache keys that leave the process only when the cache opts in.

### What the application is responsible for

- **The configuration.** Limits and thresholds come from the application. A limit raised to its hard ceiling still
  bounds memory, but it is the application's choice to spend it.
- **Names from requests.** A registry creates an instance for every new name up to its limit. Derive names from a
  closed set, not from request data, or keep the limit of the registry small.
- **Cache keys and values.** The cache keeps what it is given in memory. Do not cache secrets you would not keep in the
  heap, and bound the cache by size or weight.
- **The scope of background work.** Automatic transitions, soft time limiters, dispatcher bulkheads and the cache
  reaper run on the scope and the dispatcher the application hands in, and end with them.
- **Idempotency.** A retry or a hedge repeats a call; only repeat calls that are safe to repeat.

## Guarantees

- Every permitted call is counted exactly once by every component it passes, under retries, timeouts, cancellation
  and concurrent calls.
- A rejection of one component is never counted as a failure by another, and a retry repeats it only when told to.
- Reading the state, a snapshot, a meter or a health indicator never changes an instance.
- A wait for a permit, a slot or a load always has a timeout, and every queue of waiting callers has a capacity.
- The cancellation of a caller is passed on at once; its permits and reservations are given back.
- A failing predicate, interval function, fallback, loader, weigher, listener or entropy never breaks an instance.
- Every message, event and `toString` shows instance names, numbers and the simple type names of failures only.

## Scope

The following are **in scope**:

- input within the configured limits that makes kontinuity use unbounded memory or CPU, crash or deadlock,
- a circuit breaker that does not open or close as configured, a rate limiter that grants more permits per period than
  its limit, a bulkhead that runs more calls at once than allowed, a cache that loads a key twice at once,
- a call that is counted twice or not at all,
- a wait or a queue that grows beyond its bound,
- untrusted text that reaches an event, a meter, a `toString` or an exception message,
- a structure that grows beyond its limit, or a limit beyond its hard ceiling.

The following are **not** vulnerabilities in kontinuity:

- a vulnerability in the JDK, the JavaScript host, the Kotlin standard library, kotlinx.coroutines, Kore, konfetti or
  kollect,
- an application that raises a limit to its hard ceiling and runs out of memory within it,
- a protected call that ignores cancellation and keeps running after a timeout; cancellation in Kotlin is cooperative,
- a retry or a hedge of a call that is not safe to repeat.

## Known Limitations

- **In memory only.** Windows, budgets, caches and registries keep their state in memory and per process; there is no
  distributed rate limiting or shared breaker state.
- **Cooperative cancellation.** A hard time limiter cancels the call; code that ignores cancellation, such as a
  blocking call without interruption, delays the timeout until it returns. Use a soft time limiter or the blocking
  interop of the JVM for such code.
- `@DavilsInternal` is a compile-time opt-in, not an access boundary, and Kotlin `internal` is public in JVM bytecode.
  No `module-info.java` of kontinuity exports an `internal` package.

## Verifying a Release

Releases are signed with the Davils release key `36F578E8AD2D4391E0FA6C18154C625FAD370063` and attested by the
pipeline. Verify the signature of every artifact before you use it.
