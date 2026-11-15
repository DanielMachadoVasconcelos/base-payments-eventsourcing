# Test assessment

The reference base-payments project uses business-oriented display names, Given/When/Then comments, Spring integration scenarios, reusable providers and WireMock for issuer calls.

## Current evidence

The session suite exercises real HTTP endpoints and DynamoDB Local, including large purchases, completed retries after replacement, concurrent identical commands, conflicting content and malformed ETags. One suite also directly tests stored-document compatibility and atomic commit failure through persistence adapters. These examples prove different boundaries and deserve separate fixtures when expanded.

Aggregate tests give fast feedback on domain decisions. Mapping tests protect boundary conversions and persisted compatibility. AssertJ is already available and supports expressive assertions.

## Implemented follow-up

Added HTTP coverage for stale replacements and merchant read isolation, phase comments, four ArchUnit boundary checks and concurrent independent test classes and methods. Context-scoped isolated tables and random ports support concurrency. Small HTTP replacement setup is shared by scenarios. WireMock issuer-call coverage remains deferred until external payment calls exist.

## HTTP contract follow-up

The RestTestClient suite uses the full Spring application through a real HTTP server and an isolated DynamoDB table. Typed requests and responses cover defaults, complete replacement, validation, unknown sessions, cross-merchant replacement, scoped idempotency and correction after rejection. Malformed JSON is deliberately raw text. Problem responses are decoded into ProblemDetail and checked for status, code and correlation identity. Existing live-server and concurrent-request scenarios remain complementary evidence.

## Concurrent replacement follow-up

Real HTTP tests use the official WireMock Spring Boot integration as a proxy between the AWS SDK and real DynamoDB Local. Each fault matches a unique session or idempotency partition, and each scenario has a unique name. Automatic global reset is disabled. Test code uses injected server instances rather than WireMock's static default client.

Native WireMock scenarios inject delayed TransactionConflict cancellations for the two prepared requests, then forward subsequent transactions to DynamoDB. Tests observe both scenario transitions, verify two distinct prepared event documents with both API calls still incomplete, verify no replacement event has committed yet, and check final responses and exactly one replacement event. Fixed delays create an observation window; the state and stream assertions establish whether the intended ordering actually occurred. There is no application wait queue, custom response gate or singleton spy. These tests inject conflicts; they do not claim to observe DynamoDB's internal transaction scheduling.

HTTP cancellation responses verify Spring retry success, exhaustion and exclusion of failed conditions. A delayed empty initial result lookup covers the winner-committed-before-stream-load race. A proxied transaction response delayed beyond the SDK socket timeout proves that an API failure can follow a real commit and a repeated command recovers the persisted result. JdkClientHttpRequestFactory exposes the first API response without automatic HTTP status retries.

Earlier reservation/lease scenarios were retired with REQ-IDEM-006/007. The current implementation uses conditional transactions and framework retry without an application wait queue. See the [future WireMock authorization scenarios](authorization-test-plan.md).

## Test naming convention

Every JUnit test has an explicit `@DisplayName("Should {outcome} When {context}")` and matching `should{Outcome}When{Context}` method name. This covers domain, mapping, persistence, HTTP, simulator and architecture tests. Architecture checks run as named JUnit methods using ArchUnit rules. Requirement evidence follows the renamed methods.

## Local execution and context reuse

The reference base-payments project centralizes common Spring test configuration in a superclass. Here, each integration class previously supplied its own DynamicPropertySource and table lifecycle, and the HTTP contract suite used a different web environment. Those differences produced distinct cache keys. The default Gradle test task also excluded the compose tag, so selecting a Compose test through that task failed with "No tests found". Several Test tasks reused the same source set, producing ambiguous IDE task choices.

Full application suites now extend [SpringBootIntegrationTest](../common/src/test/java/lab/testing/SpringBootIntegrationTest.java). Ordinary payment HTTP contract and persistence classes share one live-server context. The concurrent replacement suite uses that same context and proxy, without bean overrides. Mapping tests retain a separate lightweight Spring context; the simulator has its own application and test JVM. Native Spring caching does not span separate executions or JVMs.

Each context owns its random table and cleanup. An ordered ContextClosedEvent listener deletes the table before WireMock stops and the Dynamo client closes. Class-level cleanup would invalidate a table still used by another class sharing the cached context. HTTP clients use Boot's LocalTestWebServer URI factory and JDK transport, preserving observable first-response status codes.

The standard test task runs all tests. A cold CI run exposed parallel module JVMs racing to create the same Compose container. The existing root composeUp task now provisions that dependency once before Gradle test tasks start; test methods remain parallel. integrationTest is a compatibility alias, and the empty processTest task was removed. Boot's integration-test profile starts or reuses DynamoDB through the existing Compose definition and leaves the shared container running. Docker must be available. Reload Gradle in IntelliJ after the task change. Native JUnit execution requires the repository root as its working directory.

Independent methods and classes execute concurrently. JUnit fixed parallelism and maximum pool size are both two per module JVM; parallelism alone permits compensation threads beyond that size. Concurrency scenarios also run in parallel; session-scoped WireMock rules and unique scenario names isolate their HTTP faults. Each scenario sends concurrent HTTP requests.

Local validation passed all seven filtered concurrency scenarios and the full 81-test suite with no failures, errors or skips. Temporary Gradle timing callbacks confirmed two concurrency scenarios executing simultaneously. Context cleanup completed without shutdown warnings. Local results do not establish throughput, cold startup or process-restart guarantees.

WireMock issuer scenarios remain deferred until payments-app actually calls an external payment HTTP service. The existing WireMock proxy exercises the persistence HTTP boundary only.

## Boundaries

The follow-up adds ArchUnit as a test dependency and preserves payment behavior. Simulator scenarios establish simulator guarantees. Application authorization and recovery still require implementation and application-level evidence.

Use the existing Compose infrastructure and isolated tables. Retain focused domain and compatibility tests alongside API scenarios.
