# Agent working memory

A short handover, not a transcript. [Requirements](requirements.md) own scope/evidence; [ADRs](decisions/README.md) own rationale.

## Durable context

- This is a local design experiment, not a production gateway.
- The working slice is create/read/replace session, event replay, chunked immutable purchase storage and completed-command idempotency.
- The simulator persists independent effects. Payments-app does not call it yet.
- Inspect existing Spring Boot starters, Spring Framework and SDK/service capabilities before writing technical helpers. Prefer supported framework features and maintained libraries, and document any custom-code gap in the PR. See AGENTS.md and CONTRIBUTING.md.
- Use DDD names, aggregate commands, injected JSON mapping, MapStruct, records and restrained Lombok. Follow [parameter rules](parameter-guidelines.md).
- Compose is the only dependency definition. Shared common code is source compiled into both modules.

- HTTP contract tests use MVC-backed RestTestClient with real DynamoDB and typed request/response records; raw text is used for malformed JSON. Existing live-server scenarios remain in place.
- All JUnit tests use explicit Should ... When ... display names and matching should...When... method names, including architecture checks.
- Tests use ArchUnit boundary checks and two concurrent JUnit class workers, with methods sequential and isolated integration tables.

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
- A registered process-test task is not actual restart coverage. Prose Gherkin is not Cucumber coverage.

## Next boundary, not an instruction

Durable authorization intent/recovery is next. Authorization/recovery lease fencing, application order uniqueness, outbox, 49-hour post-authorization expiry, retained idempotency cleanup and isolated full projection rebuild still need implementation and evidence.

Concurrent-request fixtures control commit order, and retry tests inject SDK conflicts before real persistence. Process restarts remain unproven. WireMock authorization scenarios remain planned in docs/authorization-test-plan.md.

Only implement the current authorized scope. Update this page when these facts change; keep it short.
