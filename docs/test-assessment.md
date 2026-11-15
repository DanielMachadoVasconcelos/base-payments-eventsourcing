# Test assessment

The reference base-payments project uses business-oriented display names, Given/When/Then comments, Spring integration scenarios, reusable providers and WireMock for issuer calls.

## Current evidence

The session suite exercises real HTTP endpoints and DynamoDB Local, including large purchases, completed retries after replacement, concurrent identical commands, conflicting content and malformed ETags. One suite also directly tests stored-document compatibility and atomic commit failure through persistence adapters. These examples prove different boundaries and deserve separate fixtures when expanded.

Aggregate tests give fast feedback on domain decisions. Mapping tests protect boundary conversions and persisted compatibility. AssertJ is already available and supports expressive assertions.

## Implemented follow-up

Added HTTP coverage for stale replacements and merchant read isolation, phase comments, four ArchUnit boundary checks and concurrent independent test classes and methods. Context-scoped isolated tables and random ports support concurrency. Small HTTP replacement setup is shared by scenarios. WireMock remains deferred until external application calls exist.

## HTTP contract follow-up

The RestTestClient suite uses the full Spring application through a real HTTP server and an isolated DynamoDB table. Typed requests and responses cover defaults, complete replacement, validation, unknown sessions, cross-merchant replacement, scoped idempotency and correction after rejection. Malformed JSON is deliberately raw text. Problem responses are decoded into ProblemDetail and checked for status, code and correlation identity. Existing live-server and concurrent-request scenarios remain complementary evidence.

## Concurrent replacement follow-up

Real HTTP tests use MockitoSpyBean around real DynamoDB and event-store beans. Test-local CompletableFuture gates and Awaitility establish two requests with the same starting version, then release their transaction calls in a deterministic order. Matching content resolves the winning result and different content returns 409 with one replacement event.

SDK-boundary failure injection proves native Spring retry success, exhaustion and exclusion of conditional failures. A separate scenario covers a winner committed between initial lookup and stream load. Lost commit responses resolve through the completed RESULT. JdkClientHttpRequestFactory exposes the first response without automatic HTTP status retries.

Earlier reservation/lease scenarios were retired with REQ-IDEM-006/007. The current implementation uses conditional transactions and framework retry without an application wait queue. See the [future WireMock authorization scenarios](authorization-test-plan.md).

## Test naming convention

Every JUnit test has an explicit `@DisplayName("Should {outcome} When {context}")` and matching `should{Outcome}When{Context}` method name. This covers domain, mapping, persistence, HTTP, simulator and architecture tests. Architecture checks run as named JUnit methods using ArchUnit rules. Requirement evidence follows the renamed methods.

## Local execution and context reuse

The reference base-payments project centralizes common Spring test configuration in a superclass. Here, each integration class previously supplied its own DynamicPropertySource and table lifecycle, and the HTTP contract suite used a different web environment. Those differences produced distinct cache keys. The default Gradle test task also excluded the compose tag, so selecting a Compose test through that task failed with "No tests found". Several Test tasks reused the same source set, producing ambiguous IDE task choices.

Full application suites now extend [SpringBootIntegrationTest](../common/src/test/java/lab/testing/SpringBootIntegrationTest.java). Ordinary payment HTTP contract and persistence classes share one live-server context. MockitoSpyBean overrides require a second payment application context. Mapping tests retain a separate lightweight Spring context; the simulator has its own application and test JVM. Native Spring caching does not span separate executions or JVMs.

Each context owns its random table and cleanup. A DisposableBean deletes the table before its Dynamo client closes. Class-level cleanup would invalidate a table still used by another class sharing the cached context. HTTP clients use Boot's LocalTestWebServer URI factory and JDK transport, preserving observable first-response status codes.

The standard test task runs all tests. integrationTest is a compatibility alias, and the empty processTest task was removed. Boot's integration-test profile starts or reuses DynamoDB through the existing Compose definition and leaves the shared container running. Docker must be available. Reload Gradle in IntelliJ after the task change. Native JUnit execution requires the repository root as its working directory.

Independent methods and classes execute concurrently. JUnit fixed parallelism and maximum pool size are both two per module JVM; parallelism alone permits compensation threads beyond that size. Concurrent replacement scenarios mutate singleton spies, so a native ResourceLock on child tests serializes their lifecycle without blocking unrelated suites. Each scenario still sends concurrent HTTP requests.

A filtered local run of PaymentSessionHttpContractTest passed all 28 executions. Temporary Gradle start/end timing callbacks confirmed overlapping executions with at most two active tests. The full method-parallel check passed 81 executions with no failures or skips. Timing callbacks recorded a maximum of two simultaneous executions in each module and one in the seven spy scenarios. No context-owned test tables remained after shutdown. These observations are local functional evidence, not a throughput benchmark or proof of process recovery.

WireMock remains deferred until payments-app actually calls an external HTTP service. Its future scenarios should cover lost responses and recovery through the public API.

## Boundaries

The follow-up adds ArchUnit as a test dependency and preserves payment behavior. Simulator scenarios establish simulator guarantees. Application authorization and recovery still require implementation and application-level evidence.

Use the existing Compose infrastructure and isolated tables. Retain focused domain and compatibility tests alongside API scenarios.
