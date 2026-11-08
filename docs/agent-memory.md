# Agent working memory

A short handover, not a transcript. [Requirements](requirements.md) own scope/evidence; [ADRs](decisions/README.md) own rationale.

## Durable context

- This is a local design experiment, not a production gateway.
- The working slice is create/read/replace session, event replay, chunked immutable purchase storage and completed-command idempotency.
- The simulator persists independent effects. Payments-app does not call it yet.
- Use DDD names, aggregate commands, injected JSON mapping, MapStruct, records and restrained Lombok. Follow [parameter rules](parameter-guidelines.md).
- Compose is the only dependency definition. Shared common code is source compiled into both modules.

- Tests use ArchUnit boundary checks and two concurrent JUnit class workers, with methods sequential and isolated integration tables.

## Sharp edges

- GET replays events, not HEAD. HEAD is written atomically but is not the read source.
- Stored event compatibility is tested. Internal refactoring must not silently change JSON or idempotency addresses.
- Completed retries use the persisted winner's payload/identifiers, not a losing caller's new session.
- Payload uploads are separate from the session transaction. Crashes/conflicts can leave orphans; cleanup is absent.
- Chunking buffers complete documents in memory. Large merchantReference metadata is not offloaded from every summary. Do not claim infinite request sizes.
- Stale versions currently return 409; missing If-Match returns 400. Planned 412/428 are not implemented.
- Fingerprinting is not a proven general-purpose canonical JSON implementation; do not claim equivalence for every reordered/numeric representation.
- Domain errors avoid Spring/AWS types but inherit HTTP-status-bearing common exceptions.
- A registered process-test task is not actual restart coverage. Prose Gherkin is not Cucumber coverage.

## Next boundary, not an instruction

Durable authorization intent/recovery is next. Lease fencing, application order uniqueness, outbox, 49-hour post-authorization expiry, retained idempotency cleanup and isolated full projection rebuild still need implementation and evidence.

Only implement the current authorized scope. Update this page when these facts change; keep it short.
