# ADR-0008 — Conditional DynamoDB transactions with native Spring retry

Status: Accepted

Supersedes [ADR-0007](0007-concurrent-replacement-reservations.md).

## Context

The reservation coordinator introduced custom polling, command leases and ownership fencing to guarantee synchronous waiting. The project prefers supported framework and SDK features. DynamoDB already conditionally commits the stream, projections and completed response together. Spring Framework 7 provides declarative retry.

## Decision

Remove the coordinator, reservation port/adapter, reservation fields and PROCESSING writes. Keep the existing event, RESULT, payload and idempotency-key formats.

Enable Spring resilience and annotate repository save with Retryable. Retry only TransactionCanceledException whose reasons contain TransactionConflict and otherwise only None. DynamoDbTransactionConflictPredicate is a small implementation of Spring's MethodRetryPredicate extension point because one SDK exception type represents both transient conflicts and permanent failures. It classifies reasons without scheduling retries or implementing a wait loop.

The repository checks RESULT before each attempt and after transaction cancellation. Fingerprint comparison chooses replay or business conflict. A service-level version mismatch rechecks RESULT to close the race between initial result lookup and stream load. Aggregate guards still decide version validity.

Use three retries after the first attempt by default, with 50ms initial delay, exponential multiplier 2 and 10ms jitter. Retry properties are configurable. Existing SDK transport retries continue to apply, so end-to-end latency includes their budget. Repeated unresolved contention returns the existing 503 STORAGE_UNAVAILABLE. A client may retry the same key.

## Consequences and alternatives

The winning committed transaction establishes the result. HTTP arrival order does not confer ownership, and there is no native per-resource FIFO wait queue. Synchronous waiting and lease requirements REQ-IDEM-006/007 are superseded/retired by REQ-IDEM-008/009.

A stable DynamoDB ClientRequestToken alone cannot reproduce the complete HTTP result across independent commands whose generated identifiers, payload references and timestamps differ. Existing durable results remain necessary beyond the native token's ten-minute window. SDK retries reuse the prepared SDK request token within a transaction call.

No new library is needed. Spring resilience already exists in the configured Framework version. Avoid applying retry to all transaction cancellations or layering an unrestricted application retry around every SDK error. Retries operate on the prepared aggregate, without replaying purchase upload or business decisions on each repository attempt.

PROCESSING rows left by the experimental revision are ignored. Automated cleanup and payment authorization/recovery remain separate work. Payload publication remains outside the transaction and may leave orphans after losing attempts.

## Evidence and links

- [Requirements](../requirements.md) REQ-IDEM-008 and REQ-IDEM-009.
- [Concurrent HTTP scenarios](../../payments-app/src/test/java/lab/payments/payment_session/ConcurrentPurchaseReplacementTest.java) use real DynamoDB Local and controlled test-only commit order.
- Transient cancellation injection proves retry execution through the real Spring proxy, plus exhaustion and conditional-failure exclusion.
- Actual JVM restarts, distributed authorization ownership and WireMock authorization scenarios remain planned.
