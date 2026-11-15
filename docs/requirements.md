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

Evidence: [shouldReplayTheOriginalCreationResponseWhenThePurchaseHasSubsequentlyChanged()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

Additional replacement evidence: [shouldReplaceTheEntirePurchaseAndResetOmittedFieldsWhenTheCurrentVersionIsSupplied()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java)

### REQ-SES-002 — Reject stale changes without new events

Status: Verified

Acceptance: Given an obsolete version, when a change is requested, then the aggregate rejects it without changing state or emitting an event.

Implementation: [PaymentSessionAggregate.java](../payments-app/src/main/java/lab/payments/payment_session/domain/PaymentSessionAggregate.java)

Evidence: [shouldPreserveStateWithoutNewEventsWhenAPurchaseChangeUsesAStaleVersion()](../payments-app/src/test/java/lab/payments/payment_session/domain/PaymentSessionAggregateBddTest.java)

Additional HTTP evidence: [shouldPreserveTheWinningPurchaseWhenAReplacementUsesAStaleVersion()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-SES-003 — Enforce the exact expiration boundary

Status: Verified

Acceptance: Given the session expiry instant, when a purchase change is attempted at that instant, then the command is rejected. The default inactivity policy is 48 hours.

Implementation: [SessionExpirationPolicy.java](../payments-app/src/main/java/lab/payments/payment_session/domain/SessionExpirationPolicy.java)

Evidence: [shouldRejectThePurchaseChangeWhenTheSessionReachesItsExactExpiryInstant()](../payments-app/src/test/java/lab/payments/payment_session/domain/PaymentSessionAggregateBddTest.java)

### REQ-SES-004 — Isolate merchant session reads

Status: Verified

Acceptance: Given a session belonging to one merchant, when another merchant reads its identifier, then the API returns 404 and the owner can still read the session.

Implementation: [GetPaymentSessionService.java](../payments-app/src/main/java/lab/payments/payment_session/application/GetPaymentSessionService.java)

Evidence: [shouldReturnSessionNotFoundWhenAnotherMerchantReadsTheSession()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-IDEM-001 — Replay the original completed response

Status: Verified

Acceptance: Given a completed creation followed by a purchase change, when the original creation is retried, then the original session/purchase response is returned, not the current purchase.

Implementation: [CreatePaymentSessionService.java](../payments-app/src/main/java/lab/payments/payment_session/application/CreatePaymentSessionService.java)

Evidence: [shouldReplayTheOriginalCreationResponseWhenThePurchaseHasSubsequentlyChanged()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-IDEM-004 — Isolate completed command keys by merchant

Status: Verified

Acceptance: Given different merchants using the same creation key, when each retries, then each receives its own original session response.

Implementation: [CreatePaymentSessionService.java](../payments-app/src/main/java/lab/payments/payment_session/application/CreatePaymentSessionService.java)

Evidence: [shouldCreateIndependentSessionsAndReplayEachOriginalResponseWhenMerchantsShareAKey()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java)

### REQ-IDEM-005 — Allow correction after rejected input or stale versions

Status: Verified

Acceptance: Given malformed JSON or a stale replacement, when corrected with the same key, then the valid command can complete. Rejected requests do not reserve a completed result.

Implementation: [ChangePurchaseService.java](../payments-app/src/main/java/lab/payments/payment_session/application/ChangePurchaseService.java)

Evidence: [shouldRejectMalformedJSONAndAllowCorrectionWhenTheSameKeyIsRetried()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java), [shouldAcceptTheCorrectedReplacementWhenItsKeyWasPreviouslyRejectedForAStaleVersion()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java)

### REQ-IDEM-002 — Reject changed content under the same key

Status: Verified

Acceptance: Given a completed command, when its scoped key is reused with different content, then the request conflicts and the original session is preserved.

Implementation: [DynamoDbPaymentSessionRepository.java](../payments-app/src/main/java/lab/payments/payment_session/infrastructure/DynamoDbPaymentSessionRepository.java)

Evidence: [shouldRejectConflictingContentWhenTheSameIdempotencyKeyIsReused()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-IDEM-003 — Reconcile a concurrent same-key winner

Status: Verified

Acceptance: Given concurrent identical creates, when responses succeed, then they name the same persisted session; unresolved requests may return 503 and must retry the same key.

Implementation: [DynamoDbPaymentSessionRepository.java](../payments-app/src/main/java/lab/payments/payment_session/infrastructure/DynamoDbPaymentSessionRepository.java)

Evidence: [shouldReturnTheSameSessionForEverySuccessfulResponseWhenIdenticalCreationCommandsRace()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-IDEM-006 — Wait for a concurrent purchase replacement

Status: Superseded

The synchronous wait and command-reservation design was replaced by REQ-IDEM-008 and REQ-IDEM-009. HTTP arrival order does not reserve precedence. See [ADR-0008](decisions/0008-native-transaction-retries.md).

### REQ-IDEM-007 — Fence expired replacement owners

Status: Retired

Purchase replacement no longer creates PROCESSING records or application leases. DynamoDB transaction conditions protect the stream and completed result under REQ-IDEM-008. External-effect ownership/recovery remains planned under REQ-REC-001. Historical rationale is preserved in [ADR-0007](decisions/0007-concurrent-replacement-reservations.md).

### REQ-IDEM-008 — Resolve concurrent replacements using conditional transactions

Status: Verified

Acceptance: Given overlapping replacements prepared from the same stream version and scoped key, when one transaction commits, then identical content resolves the persisted winner and different content receives 409 IDEMPOTENCY_KEY_CONFLICT. The stream gains one replacement event and projections advance once. HTTP arrival order does not determine the winner. A winner committed between initial result lookup and stream load is reconciled before reporting a stale version.

Implementation: [DynamoDbPaymentSessionRepository.java](../payments-app/src/main/java/lab/payments/payment_session/infrastructure/DynamoDbPaymentSessionRepository.java), [ChangePurchaseService.java](../payments-app/src/main/java/lab/payments/payment_session/application/ChangePurchaseService.java)

Evidence: [shouldReplayTheWinningReplacementWhenIdenticalConcurrentRequestsPrepareTheSameStreamVersion()](../payments-app/src/test/java/lab/payments/payment_session/ConcurrentPurchaseReplacementTest.java), [shouldRejectConflictingContentWhenConcurrentReplacementsShareAnIdempotencyKey()](../payments-app/src/test/java/lab/payments/payment_session/ConcurrentPurchaseReplacementTest.java), [shouldReplayTheWinningResultWhenAConcurrentCommitFollowsTheInitialIdempotencyLookup()](../payments-app/src/test/java/lab/payments/payment_session/ConcurrentPurchaseReplacementTest.java)

### REQ-IDEM-009 — Bound transient transaction retries through Spring

Status: Verified

Acceptance: Given a transaction cancellation containing TransactionConflict and otherwise only None reasons, when no completed result is available, then Spring retries repository persistence with bounded exponential backoff. A conditional or other permanent cancellation is not selected for retry. Exhaustion returns 503 and the caller can retry the same key. A lost commit response is recovered from the completed result.

Implementation: [PaymentSessionPolicies.java](../payments-app/src/main/java/lab/payments/configuration/PaymentSessionPolicies.java), [DynamoDbTransactionConflictPredicate.java](../payments-app/src/main/java/lab/payments/payment_session/infrastructure/DynamoDbTransactionConflictPredicate.java)

Evidence: [shouldCompleteTheReplacementWhenSpringRetriesTransientDynamoDBTransactionConflicts()](../payments-app/src/test/java/lab/payments/payment_session/ConcurrentPurchaseReplacementTest.java), [shouldReturnARetryableHTTPFailureWhenSpringExhaustsTransactionConflictRetries()](../payments-app/src/test/java/lab/payments/payment_session/ConcurrentPurchaseReplacementTest.java), [shouldAvoidRetryingTheTransactionWhenCancellationReportsAFailedCondition()](../payments-app/src/test/java/lab/payments/payment_session/ConcurrentPurchaseReplacementTest.java), [shouldReplayTheCompletedReplacementWhenTheCommitResponseIsLost()](../payments-app/src/test/java/lab/payments/payment_session/ConcurrentPurchaseReplacementTest.java)

Transient failures are injected at the SDK transaction boundary and successful attempts persist to real DynamoDB Local. The overlapping-request tests control commit order inside test fixtures. They establish deterministic conflicting snapshots, without claiming a native DynamoDB wait queue or simultaneous server-side transaction scheduling.

### REQ-EVT-001 — Replay stored facts without renewing them

Status: Verified

Acceptance: Given creation and purchase-change events, when the aggregate is rehydrated, then purchase, version and original expiry are restored with no new uncommitted events.

Implementation: [PaymentSessionAggregate.java](../payments-app/src/main/java/lab/payments/payment_session/domain/PaymentSessionAggregate.java)

Evidence: [shouldRestoreThePurchaseVersionAndRecordedExpiryWithoutNewEventsWhenReplayingAPurchaseChange()](../payments-app/src/test/java/lab/payments/payment_session/domain/PaymentSessionAggregateBddTest.java)

### REQ-EVT-002 — Commit stream and command result atomically

Status: Verified

Acceptance: Given a stale transaction, when its condition fails, then no partial event, projection or idempotency result is written. Payload uploads are outside this transaction.

Implementation: [DynamoDbPaymentSessionRepository.java](../payments-app/src/main/java/lab/payments/payment_session/infrastructure/DynamoDbPaymentSessionRepository.java)

Evidence: [shouldPreserveTheWinningTransactionWithoutPartialWritesWhenAStaleCommitFails()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-EVT-003 — Preserve persisted event compatibility

Status: Verified

Acceptance: Given an existing event document, when current code loads it, then the aggregate replays its existing flat storage schema.

Implementation: [PaymentSessionDocumentMapper.java](../payments-app/src/main/java/lab/payments/payment_session/infrastructure/PaymentSessionDocumentMapper.java)

Evidence: [shouldPreserveTheStoredEventSchemaWhenAnExistingEventDocumentIsReplayed()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-DATA-001 — Store purchase documents beyond one DynamoDB item

Status: Verified

Acceptance: Given a purchase containing a 650 KiB attribute, when created, read and retried, then its complete data is preserved through chunked immutable storage. This is not an unlimited-memory guarantee.

Implementation: [DynamoDbPurchasePayloadStore.java](../payments-app/src/main/java/lab/payments/payment_session/infrastructure/DynamoDbPurchasePayloadStore.java)

Evidence: [shouldPreserveTheEntireLargePurchaseWhenTheSessionIsReadAndCreationIsRetried()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionComposeIntegrationTest.java)

### REQ-SIM-001 — Retain an external effect after a lost response

Status: Verified

Acceptance: Given a simulator after-commit failure, when the same operation is retried, then the original response is retrieved and only one effect exists.

Implementation: [SimulatorService.java](../downstream-simulator/src/main/java/lab/simulator/SimulatorService.java)

Evidence: [shouldRetainExactlyOneEffectWhenAnOperationIsRetriedAfterALostResponse()](../downstream-simulator/src/test/java/lab/simulator/SimulatorComposeIntegrationTest.java)

### REQ-SIM-002 — Produce no effect on a before-commit failure

Status: Verified

Acceptance: Given a simulator failure before persistence, when retried, then there is no unrecorded duplicated effect.

Implementation: [SimulatorFaultPolicy.java](../downstream-simulator/src/main/java/lab/simulator/SimulatorFaultPolicy.java)

Evidence: [shouldCreateExactlyOneEffectWhenRetryingAnOperationThatFailedBeforeCommit()](../downstream-simulator/src/test/java/lab/simulator/SimulatorComposeIntegrationTest.java)

### REQ-SIM-003 — Enforce order uniqueness inside the simulator

Status: Verified

Acceptance: Given a simulated session order, when another purchase attempts an order for that session, then only the original order remains. Scope: simulator only.

Implementation: [SimulatorOperationProcessor.java](../downstream-simulator/src/main/java/lab/simulator/SimulatorOperationProcessor.java)

Evidence: [shouldPreserveTheOriginalOrderWhenAnotherPurchaseAttemptsAnOrderForTheSameSession()](../downstream-simulator/src/test/java/lab/simulator/SimulatorComposeIntegrationTest.java)

### REQ-SIM-004 — Resolve the same operation through HTTP and lookup

Status: Verified

Acceptance: Given a simulated operation submitted over HTTP, when its result is queried using the same identity, then both paths resolve the same recorded operation.

Implementation: [SimulatorController.java](../downstream-simulator/src/main/java/lab/simulator/SimulatorController.java)

Evidence: [shouldResolveTheSameRecordedOperationWhenPostingAndLookingUpItsResultOverHTTP()](../downstream-simulator/src/test/java/lab/simulator/SimulatorComposeIntegrationTest.java)

### REQ-API-002 — Validate HTTP inputs and return traceable application errors

Status: Verified

Acceptance: Given missing required headers or invalid amount, currency, merchant or idempotency inputs, when submitted through MVC, then the API returns 400. Application-generated errors include status, business code and a nonblank correlation identifier in a problem response. Unknown sessions return 404.

Implementation: [HttpErrors.java](../common/src/main/java/lab/common/HttpErrors.java)

Evidence: [shouldRejectTheRequestWhenARequiredHeaderIsMissing()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java), [shouldRejectThePurchaseWhenTheAmountIsMissingOrInvalid()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java), [shouldRejectThePurchaseWhenTheCurrencyIsMissingOrInvalid()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java), [shouldRejectTheRequestWhenTheMerchantIdentifierIsInvalid()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java), [shouldRejectTheRequestWhenTheIdempotencyKeyIsInvalid()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java), [shouldReturnABusinessCodeAndCorrelationIdentifierWhenAnApplicationRuleRejectsTheRequest()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java)

Additional session evidence: [shouldCreateASessionWithDefaultOptionalFieldsWhenOnlyRequiredPurchaseFieldsAreProvided()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java), [shouldReturnSessionNotFoundWhenReadingAnUnknownSession()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java), [shouldReturnSessionNotFoundWhenReplacingAnUnknownSession()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java), [shouldPreserveTheOwnersPurchaseWhenAnotherMerchantAttemptsToReplaceIt()](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionHttpContractTest.java)

## Adopted engineering constraints

### REQ-ENG-001 — One dependency definition

Status: Adopted

Use [compose.yaml](../compose.yaml) for development and integration dependencies. The [Gradle configuration](../build.gradle) provisions DynamoDB once before parallel module JVMs start. The [Spring Boot test profile](../common/src/test/resources/application-integration-test.yaml) starts or reuses the same definition for native IDE runs, and JUnit contexts use isolated tables. No Testcontainers.

### REQ-ENG-002 — Domain language and framework capabilities

Status: Adopted

Follow the [architecture](architecture.md), use domain command/value objects, inject Spring-managed JSON mapping and use [MapStruct configuration](../payments-app/src/main/java/lab/payments/configuration/PaymentMappingConfiguration.java), records and Lombok for appropriate boilerplate. Business decisions remain in the aggregate. Apply the [framework reuse rule](../CONTRIBUTING.md) before adding technical methods, helpers or coordinators. Prefer existing supported capabilities, official compatible Spring Boot starters and maintained libraries. Custom technical code requires a documented capability gap in the PR.

### REQ-ENG-003 — Cohesive method parameters

Status: Adopted

Prefer 0–2 parameters, allow three when cohesive, and use narrowly justified exceptions above three. [Checkstyle](../config/checkstyle/checkstyle.xml) enforces handwritten signatures; generated constructors/records require design review. See [guidelines](parameter-guidelines.md).

### REQ-ENG-004 — Documentation changes with behavior

Status: Adopted

Use the [documentation skill](../.agents/skills/maintain-project-docs/SKILL.md), [contribution workflow](../CONTRIBUTING.md) and [PR checklist](../.github/pull_request_template.md). Maintain English documentation, stable requirement IDs, test evidence and relevant diagrams/decisions in the same change.

### REQ-ENG-005 — Executable architecture boundaries

Status: Adopted

[Architecture checks](../payments-app/src/test/java/lab/payments/architecture/PaymentArchitectureTest.java) enforce domain independence from Spring, AWS and outer layers, application independence from entry and persistence implementations, and entry/persistence separation. Transitive common exception dependencies remain a documented limitation.

### REQ-ENG-006 — Isolated parallel tests

Status: Adopted

JUnit runs independent classes and methods concurrently, with parallelism and maximum pool size both set to two per module test JVM. [Payment test configuration](../payments-app/src/test/resources/junit-platform.properties) and [simulator test configuration](../downstream-simulator/src/test/resources/junit-platform.properties) use the same policy. The [shared integration fixture](../common/src/test/java/lab/testing/SpringBootIntegrationTest.java) allocates a UUID table and random HTTP port per cached context and deletes the table when that context closes. The [concurrency suite](../payments-app/src/test/java/lab/payments/payment_session/ConcurrentPurchaseReplacementTest.java) uses session-scoped WireMock HTTP fault rules and unique scenarios, without spies, global resets or serial-only locks. All full payment suites inherit [PaymentSessionIntegrationTest](../payments-app/src/test/java/lab/payments/payment_session/PaymentSessionIntegrationTest.java) and share its proxy/context.

### REQ-ENG-007 — Cohesive methods and declarative technical capabilities

Status: Adopted

Each method expresses one cohesive operation at one abstraction level. Application services coordinate named use-case steps, aggregates own business invariants and adapters own persistence details. Prefer supported framework annotations for technical capabilities and composed annotations for reusable policies with verified semantics. Preserve meaningful concurrency/error paths when extracting methods.

References: [Agent rules](../AGENTS.md), [contribution guidance](../CONTRIBUTING.md), [implemented service decomposition](service-method-assessment.md). ChangePurchaseService uses named cohesive steps and one final response reconstruction. IdempotencyRequest owns its operation-specific descriptor factory. REQ-IDEM-008/009 retain the behavioral evidence for concurrency and retry boundaries.

### REQ-ENG-008 — Readable aligned Java layout

Status: Adopted

Use four spaces, align wrapped declaration parameters beneath the first parameter and align wrapped method-chain dots with the first call's dot. Preserve the receiver/first-call line and intentional readable line breaks. [EditorConfig](../.editorconfig) supplies IntelliJ alignment settings. [Spotless](../build.gradle) cleans unused imports, trailing whitespace and final newlines. Alignment/wrapping is an IDE and review convention.

References: [Contribution guide](../CONTRIBUTING.md), [shared IntelliJ profile](../config/formatting/intellij-java.xml), [reference class](../payments-app/src/main/java/lab/payments/payment_session/application/ChangePurchaseService.java). Production and test Java sources have been processed with the native formatter. The profile uses a 120-column margin and preserves future intentional line breaks.

## Planned payment workflow

### REQ-AUT-001 — Durable authorizations and immutable snapshots

Status: Planned

See the [planned WireMock scenarios](authorization-test-plan.md) for concurrent slow-Issuer acceptance tests.

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

### REQ-ENG-009 — Standard local test execution and reusable contexts

Status: Adopted

Given Docker is running, the standard Gradle test task includes full application integration tests and Spring Boot manages DynamoDB through the existing Compose file. Compatible integration classes inherit the same configuration and reuse a cached context within one test JVM. Different applications and bean overrides retain separate contexts. integrationTest is a compatibility alias of test. Native IDE JUnit runs use the repository root as their working directory.

Implementation: [Gradle configuration](../build.gradle), [shared integration fixture](../common/src/test/java/lab/testing/SpringBootIntegrationTest.java), [test profile](../common/src/test/resources/application-integration-test.yaml). See [local execution guidance](../CONTRIBUTING.md) and [assessment](test-assessment.md).
