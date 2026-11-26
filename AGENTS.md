# AGENTS.md

Binding rules for every agent and every person working in this repository. kontinuity is the resilience library of
Davils: circuit breaker, retry, rate limiter, bulkheads, time limiter, cache, hedge, fallback and a policy that
composes them, with registries, events on [konfetti](https://github.com/davils-com/konfetti) topics and meters and
health in [kollect](https://github.com/davils-com/kollect), for Kotlin on the JVM and on WebAssembly (`wasmJs`), built
on [Kore](https://github.com/davils-com/kore). It covers the feature set of resilience4j with a Kotlin DSL and
suspending calls. It is published under the Apache License 2.0 and is built for critical infrastructure, with security
and correctness first.

## Repository

| Module | Artifact | Purpose |
|---|---|---|
| `kontinuity-core` | `com.davils:kontinuity-core` | Instance names, stages, call outcomes, rejections, failure and result filters, fallbacks, registries, the environment and the shared event and meter plumbing |
| `kontinuity-circuit-breaker` | `com.davils:kontinuity-circuit-breaker` | The circuit breaker and its registry |
| `kontinuity-retry` | `com.davils:kontinuity-retry` | The retry, its backoffs, its budget and its registry |
| `kontinuity-rate-limiter` | `com.davils:kontinuity-rate-limiter` | The rate limiter with fixed-window, sliding-window and token-bucket limits and its registry |
| `kontinuity-bulkhead` | `com.davils:kontinuity-bulkhead` | The semaphore bulkhead, the dispatcher bulkhead and their registries |
| `kontinuity-time-limiter` | `com.davils:kontinuity-time-limiter` | The time limiter with hard and soft timeouts and its registry |
| `kontinuity-cache` | `com.davils:kontinuity-cache` | The bounded in-memory cache with single-flight loading and its registry |
| `kontinuity-hedge` | `com.davils:kontinuity-hedge` | The hedge and its registry |
| `kontinuity-policy` | `com.davils:kontinuity-policy` | The `resilience { }` DSL that composes every component in a fixed order |
| `kontinuity-testing` | `com.davils:kontinuity-testing` | Test support: test environments, fault injection, counting and refusing stages and event logs |
| `kontinuity-bom` | `com.davils:kontinuity-bom` | The bill of materials of every module |
| `build-logic` | - | One convention plugin per concern, composed by `kontinuity.kotlin-library` for a library module, `kontinuity.bom` for the BOM and `kontinuity.root` for the root project; they hold nothing but `plugins { }` and `kreate { }` |

`kontinuity-core` builds on `com.davils:kore-core`, `com.davils:konfetti-core`, `com.davils:kollect-core` and
`kotlinx-coroutines-core`; every component module builds on `kontinuity-core` alone, and `kontinuity-policy` on every
component. The code is ported from the resilience library of an internal Davils project and hardened on the way.

The package of a module is `com.davils.kontinuity.<module>` without dashes, such as
`com.davils.kontinuity.circuitbreaker`, and its Java module has the same name. It is declared in
`src/jvmMain/java/module-info.java` of the module, which exports only packages with public API, never an `internal`
package. `kontinuity-testing` has no `module-info.java`, because `kore-testing` is an automatic module.

## Targets

Every library module is Kotlin Multiplatform with two targets: `jvm` and `wasmJs`. The Wasm tests run under Node.js.

- Code lives in `src/commonMain/kotlin`. What only the JVM offers, such as `ReentrantLock`, hides behind an
  `internal expect fun` in `<Role>.kt` with its `actual` in `<Role>.jvm.kt` and `<Role>.wasmJs.kt`, and a parity
  test proves that both targets behave the same. kontinuity declares no `expect` or `actual` class: they are a Beta
  language feature.
- Blocking calls and `CompletionStage` interop live in `src/jvmMain/kotlin`, because Wasm has neither threads nor
  futures.
- kontinuity never chooses a dispatcher and never starts a thread or a timer of its own: the automatic transition of
  the circuit breaker, the detached work of a soft time limiter, the dispatcher bulkhead and the reaper of the cache
  run on the scope or the dispatcher their caller hands them.

## Verify

```bash
./gradlew check
```

Nothing is done until it is green. `check` compiles with warnings as errors (Kotlin with progressive mode and extra
warnings, javac with `-Xlint:all -Werror`), runs Detekt with type resolution, the Kreate rule set and `allRules` over
the main and the test sources, the tests on the JVM and on Wasm, Kover verification at 97 % of lines, branches and
instructions, the API and semver check, the license header check, the changelog check, every Trivy scan, the
standards audit and the Kreate assurance gate, which blocks on every finding. The gate reads OSV.dev and deps.dev and
Trivy downloads its database, so `check` needs the network. The build runs on JDK 25; toolchains are never downloaded.

## Code rules

Enforced by Detekt with the Kreate rule set, the `libraries` rule set, `allRules` and warnings as errors, without a
baseline, for main and test sources alike. Build scripts and `build-logic` follow the same rules.

- **License header first.** Every `.kt`, `.kts`, `.java`, `.properties` and YAML file starts with the Apache 2.0
  header, byte for byte as in the existing files.
- **No comments.** No `//`, no `/* */` other than the license header. Names, types and tests carry the intent.
- **KDoc on public API only**, with `@param`, `@return`, `@throws` and `@since`. A new declaration gets the version it
  first ships in; existing `@since` values never change. KDoc states behaviour on invalid input, the limits that apply,
  thread safety and every security consideration.
- **No `else`.** Return early; a `when` over a sealed type or an enum is exhaustive without `else ->`. At most two
  `return` statements per function.
- **At most two chained calls.** Name intermediate results and computed conditions.
- **Named arguments** for every call with more than three arguments or with several arguments of one type.
- **One top-level type per file**, named after it; a file of functions is named after its role.
- **No package for a single file**, except the root package and `internal` packages.
- **No `!!`, no `lateinit`.**
- **Explicit API.** Everything is `internal` unless a consumer needs it. What another kontinuity module needs but a
  consumer does not is public and annotated with Kore's `@DavilsInternal`.
- **Names.** No type, file, function or property carries the name of the library as a prefix: `CircuitBreaker`, never
  `KontinuityCircuitBreaker`. The ids of the Gradle plugins keep their `kontinuity.` prefix. kontinuity uses the
  annotations of Kore, such as `DavilsInternal` and `DavilsDsl`, and declares none of its own.
- **Immutable values.** Public types are immutable. No public `data class`: a public value writes `equals`,
  `hashCode` and `toString` by hand and is listed in the `UseDataClass` excludes of `detekt.yaml`; `value class` stays
  allowed.
- **Kotlin first.** Suspending calls are the primary API, configuration is a DSL of `ValidatingBuilder`s, failures
  are chosen by reified type, and every component is also a `Flow` operator and a decorator of function types.
  Atomics come from `kotlin.concurrent.atomics` through a local `@OptIn(ExperimentalAtomicApi::class)`; experimental
  API never appears in a public signature.
- **Nothing global.** No mutable singletons, no static configuration, no global registry, no global caches, no
  generated names from a global counter, no threads or timers of the library's own.
- **No logging.** kontinuity has no logger and no log types. Telemetry runs only through the events of every instance,
  the konfetti topics and the meters in kollect; an application maps them to its own logs.

## Resilience rules

- **Every call is accounted for exactly once.** Each component counts a call as exactly one of its outcomes, such as
  successful, failed, ignored, rejected or cancelled, under retries, cancellation, timeouts and races. A change to a
  call path ships with a test of this identity.
- **A rejection is never a failure of another component.** Every refusal of a call extends `CallRejectedException`.
  A circuit breaker never records it, a retry never repeats it by default and a fallback can select it on its own.
- **Reading never changes anything.** `metrics()`, `state`, a snapshot and every meter are pure reads; a transition
  happens only on a call, on an explicit change or on the timer of a caller's scope.
- **Every wait is bounded.** Every wait for a permit, a slot or a token has a timeout and every queue of waiting
  callers has a capacity. `tryAcquire` never waits. The one exception is a caller of a cache that waits for the load
  another caller started: it waits as long as that load, which a time limiter around the loader bounds.
- **Cancellation is the caller's.** The cancellation of the caller is rethrown at once and never counted as a failure.
  A cancellation that does not come from the caller, such as the timeout of a callee, is a failure. Permits and
  reservations are given back under `NonCancellable`.
- **Fatal errors pass through.** `rethrowIfFatal` of Kore runs first in every `catch`.

## Security rules

Every input of kontinuity may come from an attacker or from a broken configuration: instance names, configuration
values, the failures and results of the protected calls and the keys of a cache.

- **Limits before work.** Instances of a registry, window sizes, attempts, delays, waiting callers, queues, detached
  work, hedged attempts and cache entries are bounded by limits with hard ceilings that no configuration exceeds.
  Nothing is truncated silently.
- **No input decides the complexity.** Grammars are `NameRule`s, never regular expressions. Maps keyed by input hold
  only checked names or the keys of a bounded cache, and every one is bounded by a limit. Cause chains are walked at
  most 32 deep and are safe against cycles.
- **Escape untrusted text exactly once, at the sink.** Every `toString` and exception message that shows text from a
  caller writes it through `SafeText` of Kore. Cache keys appear in events only when the cache opts in.
- **Foreign code is contained.** Predicates, fallbacks, interval functions, loaders, weighers and the entropy run
  inside `contained` or `attempted` of Kore. Their failure becomes a counted failure of the call, never a broken
  component.
- **Randomness is secure by default.** The jitter of retries and waits comes from the `Entropy` of konfetti, an
  entropy of Kore since konfetti 1.1.0, which defaults to `SecureEntropy` of Kore; the arithmetic of the backoffs and
  their jitter is the one of Kore, never a copy of its own.
- **Hide what Java must not reach.** The runtime lives in `internal` packages that `module-info.java` never exports.
- **Every sink has an injection test** with hostile text, and every limit has a test at the limit and one past it.

## Tests

- Kotest `FunSpec` only, Kotest assertions only, no mocking library.
- Tests live in `src/commonUnitTest/kotlin`, which runs on the JVM and on Wasm, in the package of the code they test.
  Tests that need real threads or the JDK, such as the race tests, live in `src/jvmUnitTest/kotlin`. A `fixture`
  folder holds at least two files. Shared test support lives in `kontinuity-testing`, never in main code.
- Virtual time through the test scheduler of Kotest.
- Every change ships with its tests; a fixed weakness ships with a test that would have caught it.

## Build

- A library module applies exactly one plugin, `id("kontinuity.kotlin-library")`, and declares nothing but its
  description and its dependencies; the BOM applies `id("kontinuity.bom")`.
- `build-logic` holds no Kotlin classes, no tasks and no Gradle API calls beyond reading the version catalog. Every
  plugin configures one concern and applies `kontinuity.base`:

  | Plugin | Configures |
  |---|---|
  | `kontinuity.base` | The Kreate plugin, the group and the version |
  | `kontinuity.dependency-locking` | Strict locking of every configuration |
  | `kontinuity.git-hooks` | The Git hooks |
  | `kontinuity.trivy` | Every Trivy scan |
  | `kontinuity.assurance` | The assurance gate and its `ignore` rules |
  | `kontinuity.standards` | The standards kontinuity follows, reviewed in `standards.lock` |
  | `kontinuity.platform` | The JDK, the Kotlin compiler, the targets and the Java module name |
  | `kontinuity.web-tools` | Node.js and Yarn for the Wasm target |
  | `kontinuity.testing` | Kotest and the test suites |
  | `kontinuity.detekt` | Detekt and its reports |
  | `kontinuity.coverage` | Kover and its verification |
  | `kontinuity.api-validation` | The API dump and the semver check |
  | `kontinuity.docs` | Dokka |
  | `kontinuity.publish` | The POM, the release guard and the Davils repository |
  | `kontinuity.archives` | The legal files and the manifest of every archive |
  | `kontinuity.tool-classpaths` | The version floors on the class paths of the build tools |
  | `kontinuity.changelog` | The changelog check |
  | `kontinuity.license-header` | The license header check |
  | `kontinuity.bom` | The Java platform of the BOM and its publication |

- Dependencies are declared in `gradle/libs.versions.toml`. Libraries of JetBrains, Kore, konfetti and kollect may be
  added when they are needed; every other dependency needs the approval of a maintainer.
- Only `repo.davils.com` serves `com.davils`, through `exclusiveContent`; nothing is resolved from `mavenLocal()`.
- After a dependency changes, refresh the locks and the verification metadata in one run and review what changed:

  ```bash
  ./gradlew kreateResolveAndLockAll :build-logic:dependencies --write-locks --write-verification-metadata pgp,sha256 --export-keys --no-configuration-cache
  ```

- kontinuity builds only with released versions of Kreate, Kore, konfetti and kollect, trusted by the Davils release
  key `36F578E8AD2D4391E0FA6C18154C625FAD370063`; no trust rule for unsigned snapshots is ever added.
- An `ignore` rule of the assurance gate has a reason, the exact module and an expiry no more than six months ahead.
  CRITICAL and HIGH findings can never be ignored.
- Every standard kontinuity follows is declared in `build-logic/src/main/kotlin/kontinuity.standards.gradle.kts` and
  reviewed with `./gradlew kreateStandardsReview --standard=<id> --note="..."`.

## Kore internals

kontinuity opts into these `@DavilsInternal` declarations of Kore; Kore changes them only in a major version:
`SafeText.text`, `SafeText.quoted`, `immutableList`, `immutableMap`, on the JVM `blocking` of
`com.davils.kore.core.concurrency`, and since 1.1.0 `guard`, `grownDelay`, `fullJitter`, `equalJitter`,
`proportionalJitter` and `decorrelatedJitter`. A new use is added here and to the table of Kore's `AGENTS.md`.

## Public API

The `api/*.api` dumps are the contract. `kreateApiCheck` fails on every unrecorded change and `kreateApiSemverCheck`
on a version that does not match it. A deliberate change is recorded with `./gradlew kreateApiDump` and gets a
`CHANGELOG.md` entry. A breaking change needs a major version and a commit marked as breaking. Deprecate before
removing: `@Deprecated` with a `ReplaceWith`, for at least one minor release. Every module carries the same version.

## Releases

The version lives in `gradle.properties` and nowhere else; `kreateReleaseGuard` refuses every other version. Every
build without a release tag is a `-SNAPSHOT`. A release is a tag without a `v`, such as `1.0.0`, on a commit of `main`.
Snapshots go to `https://repo.davils.com/snapshots`, releases to `https://repo.davils.com/releases`, never to Maven
Central. The pipeline publishes with `publishAllPublicationsToDavilsRepository --no-configuration-cache` and signs
with `ORG_GRADLE_PROJECT_signingInMemoryKey`, `…KeyId` and `…KeyPassword`.

## Git

Work on `develop`, release from `main`. Conventional Commits in English with the module as scope, such as
`feat(circuit-breaker): ...` or `build: ...`. Small commits, one concern each. Commit only when asked. No
`Co-Authored-By` trailer and no other attribution to tools or AI assistants.
