# ADR-0007 — Durable ownership of concurrent purchase replacements

Status: Superseded by [ADR-0008](0008-native-transaction-retries.md)

This records the historical reservation design. Its implementation was removed in favor of native conditional transactions and Spring retry. The rationale below describes that earlier decision.

## Context

REQ-IDEM-006 requires a second PUT sharing a key to wait for the first replacement and resolve its persisted result. Existing completed-result reconciliation alone can expose a transaction conflict before the winner completes. REQ-IDEM-007 requires stale owners to be fenced.

## Decision

Use a conditional PROCESSING item under the existing merchant/operation/version/key partition. Acquisition atomically checks RESULT is absent and PROCESSING is absent or expired. A unique owner token and wall-clock lease deadline identify the right to execute.

The application coordinator checks completed results before acquisition and polls when acquisition loses. It keeps the database transaction outside the wait. Both matching and conflicting requests wait until a durable result exists; the existing fingerprint check determines replay or conflict. The aggregate retains purchase/version/expiration decisions.

The final session transaction conditionally deletes PROCESSING using the owner token and unexpired lease alongside events, HEAD, META, RESULT and reverse references. An expired owner cannot write, even before takeover. Known business rejection conditionally releases ownership. Uncertain persistence failures leave it to expire. Retry checks the durable result before attempting takeover.

Existing RESULT, event, payload and idempotency-key formats are preserved. CompletedSessionCommand retains its two-argument construction for creation and existing callers; coordinated replacements additionally carry the reservation. Payload publication remains separate.

## Consequences and alternatives

DynamoDB mediates ownership across callers using the same table. This avoids relying on a JVM-local lock. Polling adds reads and failed conditional acquisition attempts under contention. Wait defaults to 3s, polling to 25ms and lease to 30s, without heartbeat renewal. Owner execution can exceed the waiter's deadline. Expired rows are reclaimed on retry; a background cleanup worker is absent.

A slow replacement that exceeds its lease must retry. Hosts require sufficiently synchronized clocks for wall-clock lease deadlines. Owner-token fencing also protects against takeover. Requests using different keys still compete through optimistic stream-version conditions.

A durable reservation handles coordination; delaying an SDK call alone would not provide it. Authorization intent and external-effect recovery remain separate planned work.

## Evidence and links

- [Requirements](../requirements.md) REQ-IDEM-006 and REQ-IDEM-007.
- [HTTP concurrency scenarios](https://github.com/DanielMachadoVasconcelos/base-payments-eventsourcing/blob/b5d002d/payments-app/src/test/java/lab/payments/payment_session/ConcurrentPurchaseReplacementTest.java) use RestTestClient, MockitoSpyBean, Awaitility and test-local CompletableFuture control.
- [Reservation integration scenarios](https://github.com/DanielMachadoVasconcelos/base-payments-eventsourcing/blob/b5d002d/payments-app/src/test/java/lab/payments/payment_session/SessionCommandReservationIntegrationTest.java) exercise real conditional transactions and forced expiry using independent adapter instances.
- Process crashes and multiple running applications remain unproven under REQ-TEST-001.
