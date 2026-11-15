# Service method assessment

Scope: ChangePurchaseService.change. This is an assessment and a proposed refactoring direction. The current implementation remains the executable behavior reference.

## Findings

The method occupies roughly 45 lines and uses five cohesive collaborators. Formatting expands nested object construction, but its main readability cost is the mixture of use-case orchestration, idempotency representation, response reconstruction and concurrent-result recovery.

| Responsibility | Current ownership | Recommended treatment |
| --- | --- | --- |
| Merchant, operation, version, key and fingerprint construction | Application service | Use a named application factory or cohesive method for the idempotency descriptor. Preserve the existing fingerprint and persisted key format. |
| Completed-response lookup | Event-store port, orchestrated by the service | Keep the replay decision visible at the use-case level. |
| Stream loading and immutable payload publication | Service through ports | Keep their ordering explicit and describe each step at one abstraction level. |
| Version, editable state and expiration decisions | Aggregate | Keep these rules in the aggregate. The service already delegates them correctly. |
| Winner committed between lookup and stream load | Service catches a version mismatch and rechecks RESULT | Preserve this consistency behavior in a clearly named operation. Re-throw the original stale-version failure if no result exists, and preserve fingerprint conflict detection. |
| Completed-result descriptor and response reconstruction | Existing result mapper plus service | Use StoredSessionResult consistently inside execution so the public entry method reconstructs the view once. |
| Atomic commit, cancellation classification and transient retries | Repository, DynamoDB and Spring Retryable | Keep these technical capabilities at the persistence boundary. |

The catch block closes a real race. Removing it to shorten the method would regress the existing concurrent lookup/load test. Moving payload publication into the session transaction would change the persistence protocol.

## Proposed refactoring direction

1. Make the public change method express the use case at one level: describe the idempotent command, resolve a completed result or execute the replacement, then reconstruct the response.
2. Give idempotency descriptor construction a cohesive name and ownership in the application layer. Evaluate reuse with CreatePaymentSessionService while preserving operation-specific fingerprints.
3. Extract the new-replacement path into a small operation that keeps stream loading, payload publication, aggregate invocation and append ordering visible.
4. Give concurrent-result reconciliation a name that exposes why a version mismatch can resolve to an earlier successful command.
5. Reuse the existing mapper and ports. Introduce a new collaborator only when it owns a cohesive responsibility with an independent reason to change.

Private helpers are appropriate when they reveal a meaningful step and reduce mixed abstraction. Avoid fragmenting each assignment into a separate method or moving the same complexity into generic callbacks. Five dependencies alone do not justify a ServiceDependencies container or an additional coordinator.

## Declarative capabilities

Use supported framework/SDK annotations and configuration for cross-cutting technical policies. The current Retryable repository method is an example. Keep the retryable error classification specific, bounded and tested through the actual Spring proxy.

Prefer a direct framework annotation for a policy with a single use. A composed annotation can express a genuinely reused technical policy when the underlying framework supports composition. Keep its attributes and behavior documented. Verify proxy invocation, self-invocation, exception matching and retry boundaries.

Business choices such as purchase replacement, expiry and concurrent-result interpretation need explicit domain/application ownership. Adding an annotation does not establish that ownership by itself. A custom idempotency annotation would require an implementation and a demonstrated gap in supported capabilities, as required by the framework reuse rule.

## General review rule

Each method should express one cohesive operation at one abstraction level. Keep methods small enough that their intent and failure paths can be understood without mentally executing infrastructure details. Use named steps where they reduce cognitive load. Apply no arbitrary line-count target.

The application service coordinates ports and domain commands. Aggregates decide business invariants. Adapters own persistence and SDK details. Frameworks own supported technical mechanisms such as retry scheduling and backoff.

When reviewing a method, ask whether each dependency and branch belongs to its responsibility, whether a framework capability can replace handwritten mechanics, and whether the proposed extraction makes the contract easier to verify. Preserve observable behavior, stored formats, atomicity, response replay and business timestamps.

## Verification for a later refactor

Run existing aggregate, mapping, HTTP and concurrency tests. In particular retain same-key replay after subsequent changes, conflicting-content rejection, winner reconciliation between lookup and load, retry exhaustion and stale-version rejection.

This assessment adds engineering guidance. It introduces no runtime capability or additional test coverage.
