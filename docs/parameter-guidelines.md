# Cohesive parameters and domain commands

This is the current rule; the [audit baseline](parameter-audit.md) records why it was introduced.

## Rule

Prefer zero to two parameters. Three are allowed when cohesive. Checkstyle rejects more than three in handwritten methods, interfaces and explicit constructors unless a local, justified suppression exists.

The numeric limit is a **project convention**, not a universal rule attributed to Martin Fowler. The useful ideas are introducing parameter objects, preserving whole objects and removing redundant arguments.

Generated MapStruct signatures are excluded. Records and Lombok-generated constructors need cohesion review: do not create ServiceDependencies merely to hide responsibility.

Current local exceptions are HTTP binding in PaymentSessionController and SimulatorController, plus the SimulatorOperationProcessor infrastructure constructor. Keep the reason next to the suppression; do not expand it to a package.

## Model a concept, not an argument bag

| Type | Meaning |
| --- | --- |
| PaymentSessionIdentity | Merchant and session address |
| PurchaseRevision | Immutable purchase summary and payload identity |
| CommandMetadata | Command identity and chosen business instant |
| OpenPaymentSession | Domain intent to open a session |
| ChangeSessionPurchase | Replacement, expected version and metadata |
| SessionExpirationPolicy | Pure configured inactivity policy |
| SessionActivity | Creation, last business activity and expiry |
| SessionWriteContext | Validated write identity and preconditions |
| IdempotencyRequest | Merchant/operation/API scope, key and fingerprint |
| CompletedSessionCommand | Request and original persistible result |

```java
PaymentSessionAggregate.create(openPaymentSession, expirationPolicy);
session.changePurchase(changeSessionPurchase, expirationPolicy);
eventStore.append(session, completedSessionCommand);
```

The aggregate receives no transport headers. The policy is explicit rather than hidden globally or stuffed into a command to reach one argument.

## Persistence and simulation

SessionCommit is local to a write, derives addresses and validates result identity before one transaction. append/save return the persisted result, including the winning result in a concurrent idempotent race.

Structured documents use records and JsonDocumentCodec with Spring's injected ObjectMapper. Dynamo.put accepts SDK Put; no duplicate builder DSL is needed.

SimulateOperation, PreparedSimulation, phase commands and SimulationScenario model actual simulation concepts. Persist effect and response before an after-effect fault.

## Review checklist

- Does the new type have a domain/boundary meaning?
- Can a value be derived rather than passed twice?
- Does the record stay immutable and local to the operation?
- Do dependencies suggest splitting responsibilities instead?
- Have signatures changed without changing HTTP/storage formats accidentally?

Run `./gradlew check` and relevant Compose integration tests. See [CONTRIBUTING](../CONTRIBUTING.md).
