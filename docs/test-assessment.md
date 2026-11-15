# Test assessment

The reference base-payments project uses business-oriented display names, Given/When/Then comments, Spring integration scenarios, reusable providers and WireMock for issuer calls.

## Current evidence

The session suite exercises real HTTP endpoints and DynamoDB Local, including large purchases, completed retries after replacement, concurrent identical commands, conflicting content and malformed ETags. One suite also directly tests stored-document compatibility and atomic commit failure through persistence adapters. These examples prove different boundaries and deserve separate fixtures when expanded.

Aggregate tests give fast feedback on domain decisions. Mapping tests protect boundary conversions and persisted compatibility. AssertJ is already available and supports expressive assertions.

## Implemented follow-up

Added HTTP coverage for stale replacements and merchant read isolation, phase comments, four ArchUnit boundary checks and concurrent test classes with sequential methods. Existing isolated tables and random ports support class concurrency. Small HTTP replacement setup is shared by scenarios. WireMock remains deferred until external application calls exist.

## HTTP contract follow-up

The RestTestClient suite uses the full Spring application through MVC and a real isolated DynamoDB table. Typed requests and responses cover defaults, complete replacement, validation, unknown sessions, cross-merchant replacement, scoped idempotency and correction after rejection. Malformed JSON is deliberately raw text. Problem responses are decoded into ProblemDetail and checked for status, code and correlation identity. Existing live-server and concurrent-request scenarios remain complementary evidence.

## Concurrent replacement follow-up

Real HTTP replacement tests use MockitoSpyBean around real event-store and reservation beans. Awaitility observes a test-local CompletableFuture gate after reservation acquisition and a second failed acquisition before the owner is released. They assert matching replay, conflicting-content rejection, waiter timeout, and failures before/after persistence. RestTestClient uses JdkClientHttpRequestFactory to expose the first response without automatic 503 retries.

Independent reservation adapters exercise takeover after explicitly expiring the native lease attribute, owner fencing and business-rejection cleanup. This is not process-restart evidence. See the [future WireMock authorization scenarios](authorization-test-plan.md).

## Test naming convention

Every JUnit test has an explicit `@DisplayName("Should {outcome} When {context}")` and matching `should{Outcome}When{Context}` method name. This covers domain, mapping, persistence, HTTP, simulator and architecture tests. Architecture checks run as named JUnit methods using ArchUnit rules. Requirement evidence follows the renamed methods.

## Further improvements

1. Add Given/When/Then sections and business display names to existing scenarios. Keep compatibility tests explicit about their storage contract.
2. Extract small purchase builders and HTTP scenario helpers when repeated setup obscures the business action. Keep aggregate commands visible in domain tests.
3. Add architecture checks for domain independence, application ports and entry-point dependencies. Account for the existing domain dependency on common HTTP-status-bearing exceptions before choosing enforceable rules.
4. Enable parallel test classes only after confirming each class has an independent table, port and cleanup lifecycle. Gradle project parallelism is enabled today; concurrent JUnit methods are not configured. Method-level concurrency needs a separate shared-state assessment.
5. Introduce WireMock when payments-app actually calls an external HTTP service. Its scenarios should cover lost responses and recovery through the public API.

## Boundaries

The follow-up adds ArchUnit as a test dependency and preserves payment behavior. Simulator scenarios establish simulator guarantees. Application authorization and recovery still require implementation and application-level evidence.

Use the existing Compose infrastructure and isolated tables. Retain focused domain and compatibility tests alongside API scenarios.
