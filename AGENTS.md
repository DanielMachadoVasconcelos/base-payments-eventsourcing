# Repository instructions

These instructions apply to this repository and its descendants. All repository documentation must be in English.

## Orient first

Read [README](README.md), [agent memory](docs/agent-memory.md) and relevant [requirements](docs/requirements.md). Read only the architecture sections and ADRs needed for the task. Do not require access to prior chats.

For a new/changed requirement, behavior, HTTP contract or architecture decision, read and follow [.agents/skills/maintain-project-docs/SKILL.md](.agents/skills/maintain-project-docs/SKILL.md) before editing. This is a repository-local skill, not a global installation.

## Preserve the design

- Follow Controller → Mapper → Command → Service → Aggregate → Event Store → Repository. Keep business decisions in the aggregate and framework/AWS dependencies outside the domain.
- Use cohesive domain commands/value objects, not parameter bags. Follow [parameter guidelines](docs/parameter-guidelines.md); do not hide dependency counts in ServiceDependencies.
- Inject Spring's JSON mapper. Prefer MapStruct, records, Lombok and existing framework/SDK capabilities when appropriate.
- Preserve conditional, atomic writes of events, metadata, projections and command results. Payload publication is separate; do not describe it as part of that transaction.
- Preserve persisted document and idempotency-key formats unless a migration is deliberately included.
- Do not put complete purchase attributes into growing event/HEAD documents. Do not promise infinite payload size.
- Every JUnit test must have an explicit `@DisplayName("Should {outcome} When {context}")` and a matching `should{Outcome}When{Context}` method name, including parameterized and architecture tests. Preserve Given/When/Then sections and update requirement evidence when renaming tests.
- Do not add Testcontainers. Use the existing Compose definition and isolated test tables.
- When implementing the future payment workflow, persist intent before external business effects. Never hold a database transaction across an HTTP call.
- Use specific business failures. Do not add LabException or expose tokens/payloads in routine logs.

## Finish honestly

Implement only the requested scope. Distinguish implemented behavior, test evidence and planned guarantees. Standalone simulator behavior does not prove a payment-app workflow.

Update requirements, affected docs/diagrams and durable memory in the same change. Add an ADR only for a material decision. Never fabricate test runs, benchmark results or implemented endpoints.

Run applicable checks from [CONTRIBUTING](CONTRIBUTING.md), including `python3 scripts/check-docs.py` for documentation changes. Report failures and unrun checks.

Keep memory short: current facts, constraints, sharp edges and next unimplemented boundary. No transcripts, personal machine paths, secrets or duplicate task diaries.
