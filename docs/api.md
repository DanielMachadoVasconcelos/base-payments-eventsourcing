# HTTP walkthrough — current contracts

These routes are implemented. Future authorization/order endpoints belong to the [roadmap](requirements.md).

## Payment sessions: port 8080

All routes require X-Merchant-Id. Writes also require Idempotency-Key.

| Method | Route | Success |
| --- | --- | --- |
| POST | `/v1/sessions` | 201 + session response + ETag |
| GET | `/v1/sessions/{sessionId}` | 200 + current response + ETag |
| PUT | `/v1/sessions/{sessionId}` | 200 + replacement response + ETag |

A purchase has positive integral amountMinor, recognized uppercase currency, optional merchantReference (default empty string) and attributes (default empty object). 10000 BRL represents R$100.00; there is no tax or currency conversion.

Start with the [creation example](../README.md). Copy its sessionId and ETag:

```bash
curl -i http://localhost:8080/v1/sessions/SESSION_ID \
  -H 'X-Merchant-Id: merchant-demo'

curl -i -X PUT http://localhost:8080/v1/sessions/SESSION_ID \
  -H 'X-Merchant-Id: merchant-demo' \
  -H 'Idempotency-Key: replace-001' \
  -H 'If-Match: "1"' \
  -H 'Content-Type: application/json' \
  --data '{"amountMinor":15000,"currency":"BRL","attributes":{"revision":2}}'
```

PUT replaces the whole purchase; omitted optional fields return to defaults. Use the actual current ETag, not always 1.

A same-key, same-command retry reproduces its original result even after later changes. Different content with the same scoped key conflicts. Keys are scoped by merchant, operation and API version. This is not authentication.

## Errors as implemented

Errors use ProblemDetail with code and correlationId.

| Status | Current behavior |
| --- | --- |
| 400 | Invalid input, malformed ETag or missing required header |
| 404 | Session not found under the supplied merchant |
| 409 | Conflicting idempotency content, stale version or non-editable session |
| 410 | Purchase change at/after session expiry |
| 503 | Durable result could not be confirmed; retry the same key |

The original design proposed 412 for stale versions and 428 for missing If-Match. They are **not current behavior**. There is no 202 asynchronous payment operation route yet.

Session inactivity defaults to 48 hours. Business changes advance activity; reads and completed retries do not. There is no payment-app public clock-advance endpoint.

## Standalone simulator: port 8081

Start it with `./gradlew :downstream-simulator:bootRun`.

- `POST /sim/{phase}`: AUTHORIZE, CONFIRM, ORDER, WEBHOOK; merchant and idempotency headers required.
- `GET /sim/results/{phase}/{key}`: retrieve recorded response under the merchant.
- Local/test controls: `POST /lab/faults`, `GET /lab/sessions/{session}/effects`, `POST /lab/clock`.

```bash
curl -i -X POST http://localhost:8081/sim/AUTHORIZE \
  -H 'X-Merchant-Id: merchant-demo' \
  -H 'Idempotency-Key: issuer-demo-001' \
  -H 'Content-Type: application/json' \
  --data '{"sessionId":"sim-demo","authorizationId":"auth-demo","purchase":{"amountMinor":10000,"currency":"BRL"}}'

curl -i http://localhost:8081/sim/results/AUTHORIZE/issuer-demo-001 \
  -H 'X-Merchant-Id: merchant-demo'
```

This creates a simulated effect, not an authorization inside payments-app.

Fault controls take sessionId, phase, mode, optional remaining and delayMs. Modes: REJECT, BEFORE_DELAY, AFTER_DELAY, FAIL_BEFORE, FAIL_AFTER. After-effect failures occur after effect and response are stored together.

The simulator clock accepts `{"instant":"2026-11-08T12:00:00Z"}` or `{"reset":true}`. It does not advance payments-app's clock. Lab controls are not production administration APIs.
