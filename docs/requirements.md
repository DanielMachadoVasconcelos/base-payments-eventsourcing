# Requirements and evidence

Stable IDs connect behavior, design and proof. This catalogue is the scope index—not a claim that the original payment design is finished.

## Status rules

- **Verified:** implemented behavior linked to an executable test. A passing local run proves only the exercised scope/environment; consult the change's verification report.
- **Adopted:** an engineering convention with a configuration/source reference, not a business workflow guarantee.
- **Planned:** agreed direction, not implemented or verified. No production guarantees are implied.
- **Retired / Superseded:** a preserved ID for behavior no longer pursued or replaced; link the replacement when applicable.

For a new requirement, copy the nearest section, allocate the next unused ID in its family and write Given/When/Then acceptance. Never reuse an ID. Evidence links use actual test method names so the documentation checker can detect renamed/deleted tests. Keep failures and untested portions explicit.

## Verified session and simulator behavior

### REQ-SES-001 — Create, read and fully replace a merchant session

Status: Verified

Acceptance: Given a session, when its purchase is replaced with the current ETag, then GET returns the new purchase and version.

Implementation: [PaymentSessionController.java](../payments-app/src/main/java/lab/payments/payment_session/entry/PaymentSessionController.java)

Evidence: [givenChangedPurchaseWhenCreationIsRetriedThenItsOriginalResultIsReplayed()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-SES-002 — Reject stale changes without new events

Status: Verified

Acceptance: Given an obsolete version, when a change is requested, then the aggregate rejects it without changing state or emitting an event.

Implementation: [PaymentSessionAggregate.java](../payments-app/src/main/java/lab/payments/payment_session/domain/PaymentSessionAggregate.java)

Evidence: [rejectStalePurchaseChange()](../payments-app/src/test/java/lab/payments/payment_session/domain/PaymentSessionAggregateBddTest.java)

### REQ-SES-003 — Enforce the exact expiration boundary

Status: Verified

Acceptance: Given the session expiry instant, when a purchase change is attempted at that instant, then the command is rejected. The default inactivity policy is 48 hours.

Implementation: [SessionExpirationPolicy.java](../payments-app/src/main/java/lab/payments/payment_session/domain/SessionExpirationPolicy.java)

Evidence: [rejectPurchaseChangeAtExpiry()](../payments-app/src/test/java/lab/payments/payment_session/domain/PaymentSessionAggregateBddTest.java)

### REQ-IDEM-001 — Replay the original completed response

Status: Verified

Acceptance: Given a completed creation followed by a purchase change, when the original creation is retried, then the original session/purchase response is returned, not the current purchase.

Implementation: [CreatePaymentSessionService.java](../payments-app/src/main/java/lab/payments/payment_session/application/CreatePaymentSessionService.java)

Evidence: [givenChangedPurchaseWhenCreationIsRetriedThenItsOriginalResultIsReplayed()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-IDEM-002 — Reject changed content under the same key

Status: Verified

Acceptance: Given a completed command, when its scoped key is reused with different content, then the request conflicts and the original session is preserved.

Implementation: [DynamoDbPaymentSessionRepository.java](../payments-app/src/main/java/lab/payments/payment_session/infrastructure/DynamoDbPaymentSessionRepository.java)

Evidence: [givenSameKeyWithDifferentContentThenTheOriginalSessionIsNotOverwritten()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-IDEM-003 — Reconcile a concurrent same-key winner

Status: Verified

Acceptance: Given concurrent identical creates, when responses succeed, then they name the same persisted session; unresolved requests may return 503 and must retry the same key.

Implementation: [DynamoDbPaymentSessionRepository.java](../payments-app/src/main/java/lab/payments/payment_session/infrastructure/DynamoDbPaymentSessionRepository.java)

Evidence: [givenConcurrentIdenticalCommandsWhenCompletedThenEverySuccessNamesTheSameSession()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-EVT-001 — Replay stored facts without renewing them

Status: Verified

Acceptance: Given creation and purchase-change events, when the aggregate is rehydrated, then purchase, version and original expiry are restored with no new uncommitted events.

Implementation: [PaymentSessionAggregate.java](../payments-app/src/main/java/lab/payments/payment_session/domain/PaymentSessionAggregate.java)

Evidence: [replayPurchaseChange()](../payments-app/src/test/java/lab/payments/payment_session/domain/PaymentSessionAggregateBddTest.java)

### REQ-EVT-002 — Commit stream and command result atomically

Status: Verified

Acceptance: Given a stale transaction, when its condition fails, then no partial event, projection or idempotency result is written. Payload uploads are outside this transaction.

Implementation: [DynamoDbPaymentSessionRepository.java](../payments-app/src/main/java/lab/payments/payment_session/infrastructure/DynamoDbPaymentSessionRepository.java)

Evidence: [givenAStaleCommitWhenItsConditionFailsThenNoPartialEventOrIdempotencyResultIsWritten()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-EVT-003 — Preserve persisted event compatibility

Status: Verified

Acceptance: Given an existing event document, when current code loads it, then the aggregate replays its existing flat storage schema.

Implementation: [PaymentSessionDocumentMapper.java](../payments-app/src/main/java/lab/payments/payment_session/infrastructure/PaymentSessionDocumentMapper.java)

Evidence: [givenExistingEventDocumentWhenReplayedThenItsStorageSchemaIsPreserved()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-DATA-001 — Store purchase documents beyond one DynamoDB item

Status: Verified

Acceptance: Given a purchase containing a 650 KiB attribute, when created, read and retried, then its complete data is preserved through chunked immutable storage. This is not an unlimited-memory guarantee.

Implementation: [DynamoDbPurchasePayloadStore.java](../payments-app/src/main/java/lab/payments/payment_session/infrastructure/DynamoDbPurchasePayloadStore.java)

Evidence: [givenLargePurchaseWhenReadAndRetriedThenItsEntireResponseIsPreserved()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-SIM-001 — Retain an external effect after a lost response

Status: Verified

Acceptance: Given a simulator after-commit failure, when the same operation is retried, then the original response is retrieved and only one effect exists.

Implementation: [SimulatorService.java](../downstream-simulator/src/main/java/lab/simulator/SimulatorService.java)

Evidence: [givenLostResponseAfterCommitWhenRetriedThenOnlyOneEffectExists()](../downstream-simulator/src/test/java/lab/simulator/SimulatorComposeIntegrationTest.java)

### REQ-SIM-002 — Produce no effect on a before-commit failure

Status: Verified

Acceptance: Given a simulator failure before persistence, when retried, then there is no unrecorded duplicated effect.

Implementation: [SimulatorFaultPolicy.java](../downstream-simulator/src/main/java/lab/simulator/SimulatorFaultPolicy.java)

Evidence: [givenFailureBeforeCommitWhenRetriedThenNoUnrecordedEffectIsDuplicated()](../downstream-simulator/src/test/java/lab/simulator/SimulatorComposeIntegrationTest.java)

### REQ-SIM-003 — Enforce order uniqueness inside the simulator

Status: Verified

Acceptance: Given a simulated session order, when another purchase attempts an order for that session, then only the original order remains. Scope: simulator only.

Implementation: [SimulatorOperationProcessor.java](../downstream-simulator/src/main/java/lab/simulator/SimulatorOperationProcessor.java)

Evidence: [givenAnOrderWhenAnotherPurchaseUsesTheSessionThenThereIsStillOnlyOneOrder()](../downstream-simulator/src/test/java/lab/simulator/SimulatorComposeIntegrationTest.java)

### REQ-SIM-004 — Resolve the same operation through HTTP and lookup

Status: Verified

Acceptance: Given a simulated operation submitted over HTTP, when its result is queried using the same identity, then both paths resolve the same recorded operation.

Implementation: [SimulatorController.java](../downstream-simulator/src/main/java/lab/simulator/SimulatorController.java)

Evidence: [givenTheExistingHttpContractWhenPostingAndLookingUpThenBothResolveTheSameOperation()](../downstream-simulator/src/test/java/lab/simulator/SimulatorComposeIntegrationTest.java)

## Adopted engineering constraints

### REQ-ENG-001 — One dependency definition

Status: Adopted

Use [compose.yaml](../compose.yaml) for development and integration dependencies. The [Gradle integration task](../build.gradle) starts that same definition and JUnit suites use isolated tables. No Testcontainers.

### REQ-ENG-002 — Domain language and framework-assisted mapping

Status: Adopted

Follow the [architecture](architecture.md), use domain command/value objects, inject Spring-managed JSON mapping and use [MapStruct configuration](../payments-app/src/main/java/lab/payments/configuration/PaymentMappingConfiguration.java), records and Lombok for appropriate boilerplate. Business decisions remain in the aggregate.

### REQ-ENG-003 — Cohesive method parameters

Status: Adopted

Prefer 0–2 parameters, allow three when cohesive, and use narrowly justified exceptions above three. [Checkstyle](../config/checkstyle/checkstyle.xml) enforces handwritten signatures; generated constructors/records require design review. See [guidelines](parameter-guidelines.md).

### REQ-ENG-004 — Documentation changes with behavior

Status: Adopted

Use the [documentation skill](../.agents/skills/maintain-project-docs/SKILL.md), [contribution workflow](../CONTRIBUTING.md) and [PR checklist](../.github/pull_request_template.md). Maintain English documentation, stable requirement IDs, test evidence and relevant diagrams/decisions in the same change.

## Planned payment workflow

### REQ-AUT-001 — Durable authorizations and immutable snapshots

Status: Planned

Acceptance target: Persist authorization intent, external identities and both channel tokens before calling the Issuer; preserve old valid authorizations after purchase replacement.

### REQ-ORD-001 — One application order using any valid authorization

Status: Planned

Acceptance target: Reserve the session conditionally; concurrent requests using old/new authorizations or different token channels must produce one logical external order using the chosen authorization's purchase.

### REQ-REC-001 — Recover unknown external results

Status: Planned

Acceptance target: Use persisted phase requests, idempotent external calls, queries, bounded retries and lease-epoch fencing across application instances. NEEDS_REVIEW must retain unresolved work.

### REQ-WEB-001 — Durable authorization webhook

Status: Planned

Acceptance target: Create an immutable outbox item with acceptance, resend with stable identity and deduplicate after delivery-before-local-ack failures.

### REQ-EXP-001 — Complete payment lifecycle expiry

Status: Planned

Acceptance target: Add authorization validity of one hour, Issuer confirmation deadline of four hours and session inactivity of 49 hours after accepted authorization. Retries/maintenance must not renew business activity.

### REQ-DEL-001 — Retention and resumable physical deletion

Status: Planned

Acceptance target: Wait for expiry, no active/unknown/review operation, no pending webhook and at least 24 hours of completed-response retention; mark PURGING and remove external references safely, with META last.

### REQ-REBUILD-001 — Rebuild all projections into an isolated target

Status: Planned

Acceptance target: Validate contiguous supported events up to captured heads; exclude purging streams; reconstruct without external calls, new IDs or renewed deadlines.

### REQ-TEST-001 — Prove crash recovery across actual processes

Status: Planned

Acceptance target: Stop/restart payments-app while preserving simulator and DynamoDB; cover unknown admission/results, lease takeover, purge interruption and webhook redelivery.

### REQ-OBS-001 — Measure contention and recovery

Status: Planned

Acceptance target: Add business/worker metrics, reproducible k6 scenarios and reported observations for one/two instances and virtual-thread configurations. No AWS capacity claims from Local.

### REQ-API-001 — Complete asynchronous API contracts

Status: Planned

Acceptance target: Add authorization/order/operation/event endpoints and an OpenAPI contract. Confirm and test the planned 412/428 error changes before documenting them as current behavior.

## Non-goals for this iteration

Real financial integrations, production authentication, AWS deployment, PostgreSQL migration, DynamoDB Streams and automatic financial compensation are outside this iteration. Removing the single-item payload ceiling does not promise unlimited request memory or unlimited inline metadata.
