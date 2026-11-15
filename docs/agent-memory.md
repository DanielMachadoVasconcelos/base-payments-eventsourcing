# Agent working memory

A short handover, not a transcript. [Requirements](requirements.md) own scope/evidence; [ADRs](decisions/README.md) own rationale.

## Durable context

- This is a local design experiment, not a production gateway.
- The working slice is create/read/replace session, event replay, chunked immutable purchase storage and completed-command idempotency.
- The simulator persists independent effects. Payments-app does not call it yet.
- Inspect existing Spring Boot starters, Spring Framework and SDK/service capabilities before writing technical helpers. Prefer supported framework features and maintained libraries, and document any custom-code gap in the PR. See AGENTS.md and CONTRIBUTING.md.
- Java layout follows the user-formatted ChangePurchaseService: aligned wrapped parameters and chain dots, with receiver/first call together. Production and test Java files were processed with the native IntelliJ profile in config/formatting/intellij-java.xml. The profile uses a 120-column margin and .editorconfig carries matching alignment settings. Spotless only cleans imports/whitespace and does not enforce wrapping.
- Keep orchestration methods small and at one abstraction level. Use named cohesive steps and supported declarative policies. ChangePurchaseService now resolves StoredSessionResult internally and maps the response once, with named replacement, aggregate-application and concurrent-result recovery steps. See docs/service-method-assessment.md.
- Use DDD names, aggregate commands, injected JSON mapping, MapStruct, records and restrained Lombok. Follow [parameter rules](parameter-guidelines.md).
- Compose is the only dependency definition. Shared common code is source compiled into both modules.

- HTTP contract tests use live-server RestTestClient with real DynamoDB and typed request/response records; raw text is used for malformed JSON. Existing live-server scenarios remain in place.
- All JUnit tests use explicit Should ... When ... display names and matching should...When... method names, including architecture checks.
- Tests use ArchUnit and concurrent independent methods/classes, capped at two JUnit workers per module JVM. All payment integration scenarios use session-scoped WireMock HTTP fault rules, with no spies or serial-only lock.
- Full integration tests extend SpringBootIntegrationTest. Compatible classes reuse the context, server and UUID table; context destruction deletes that table. PaymentSessionIntegrationTest adds official WireMock Boot integration to the common fixture; all three full payment suites share it. Automatic WireMock reset is disabled; use injected server instances and unique session/scenario matchers. Keep lightweight mapper contexts separate.
- The standard test task includes integration tests; integrationTest is an alias, not another Test task. Gradle composeUp provisions the dependency once before parallel module JVMs; Boot integration-test profile starts/reuses the shared Compose DynamoDB with start-only lifecycle. Docker must run; native IDE JUnit working directory must be the repository root.

## Sharp edges

- GET replays events, not HEAD. HEAD is written atomically but is not the read source.
- Stored event compatibility is tested. Internal refactoring must not silently change JSON or idempotency addresses.
- Concurrent replacements use DynamoDB conditional transactions and Spring Framework 7 Retryable on repository save. Retry only transient TransactionConflict cancellations, with 3 retries, 50ms initial exponential backoff and jitter by default. No coordinator, PROCESSING reservation or replacement lease remains. HTTP arrival order does not select the winner.
- Completed retries use the persisted winner's payload/identifiers, not a losing caller's new session.
- Payload uploads are separate from the session transaction. Crashes/conflicts can leave orphans; cleanup is absent.
- Chunking buffers complete documents in memory. Large merchantReference metadata is not offloaded from every summary. Do not claim infinite request sizes.
- Stale versions currently return 409; missing If-Match returns 400. Planned 412/428 are not implemented.
- Fingerprinting is not a proven general-purpose canonical JSON implementation; do not claim equivalence for every reordered/numeric representation.
- Domain errors avoid Spring/AWS types but inherit HTTP-status-bearing common exceptions.
- Process-restart coverage remains unimplemented. Prose Gherkin is not Cucumber coverage.

## Next boundary, not an instruction

Durable authorization intent/recovery is next. Authorization/recovery lease fencing, application order uniqueness, outbox, 49-hour post-authorization expiry, retained idempotency cleanup and isolated full projection rebuild still need implementation and evidence.

Concurrent-request fixtures control commit order, and retry tests inject SDK conflicts before real persistence. Process restarts remain unproven. WireMock authorization scenarios remain planned in docs/authorization-test-plan.md.

Only implement the current authorized scope. Update this page when these facts change; keep it short.
