# Planned concurrent authorization tests with WireMock

Status: Planned. Payments-app currently has no authorization endpoint or outbound Issuer call. These scenarios follow REQ-AUT-001 and REQ-REC-001 and are not executable evidence.

## Fixtures

Use SpringBootTest with a random HTTP port, RestTestClient and a real isolated DynamoDB Local table through the existing Compose definition. Register WireMock with its official Spring Boot integration using EnableWireMock, ConfigureWireMock and InjectWireMock. Override the real outbound Issuer client's base URL with WireMock's random-port URL.

Keep request/response objects typed. Persist intent and stable external request identity before making the HTTP call. No DynamoDB transaction spans that call.

## Scenarios

- **Should return the original authorization result When an identical concurrent request arrives while the issuer is responding**. Start the first command and observe its persisted intent and outgoing Issuer request. Hold the response until the second command is observed waiting on the same operation. Release it, verify both API responses resolve the same completed authorization, and assert one accepted authorization event, one durable result and one outbound call.
- **Should reject conflicting content When the same authorization key is reused while the issuer is responding**. Follow the same ordering with different second-request content. Verify the completed winner and a 409 IDEMPOTENCY_KEY_CONFLICT, one outbound call and no additional acceptance event.
- **Should recover the original authorization When the issuer commits but its response is lost**. Exercise durable external idempotency/lookup with the standalone simulator and a real recovery path. A WireMock request count alone does not prove exactly one external business effect.

## Controlling order

Use WireMock request observation and Awaitility to establish arrival. A response delay exercises latency, but a fixed delay alone cannot guarantee the second request entered the wait path before the first completed. Select a supported WireMock extension or test-controlled response gate during implementation and bound every wait. Keep synchronization inside the test support.

The current PUT tests use WireMock as an HTTP proxy to real DynamoDB Local. Session-scoped transaction cancellation responses and scenario states exercise framework retries and completed-result reconciliation. Artificial conflicts are injected at the wire boundary; they do not prove the DynamoDB scheduler itself queued a request. They do not implement a wait queue or establish the future authorization ownership protocol.

## Dependencies and remaining design

The official WireMock Spring Boot integration is already used by payment persistence tests. Reuse it for the outbound Issuer client when that phase is implemented. Define the authorization endpoint, accepted response contract, durable intent states, external idempotency identity, unknown-result recovery and operation-owner fencing first. Do not infer these guarantees from the existing simulator or current replacement transaction retries.
