# ADR-0001 — Event-sourced sessions and atomic command results

Status: Accepted

## Context

Retries and concurrent commands must not overwrite history or return an invented result. Session state must be reproducible without relying on an in-memory request.

## Decision

Keep separate META, HEAD and event items. Let the aggregate produce facts, then conditionally commit events, projections, completed idempotency result and reverse references in one DynamoDB transaction. Return the persisted winning result after an idempotent race.

Current reads rehydrate events; HEAD is maintained but is not the read source. Current event names are PaymentSessionCreated and PurchaseChanged.

## Consequences and alternatives

Compared with mutable HEAD-only storage, this retains decision history and permits deterministic aggregate replay, at the cost of explicit schemas and transaction assembly. Async-only projection updates were not chosen for essential state.

Payload uploads have a separate publication protocol. A complete isolated projection rebuild, event schema evolution protocol and purge lifecycle remain future work.

Scope/evidence: REQ-EVT-001–003 and REQ-IDEM-001–003 in the [catalogue](../requirements.md).
