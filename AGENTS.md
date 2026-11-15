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

## Reuse framework capabilities

- Before writing a technical helper, method, wrapper or coordinator, inspect the existing code, configured Spring Boot starters, Spring Framework APIs and AWS SDK/service capabilities for the required behavior.
- Prefer an existing supported capability. For a missing capability, prefer an official compatible Spring Boot starter, then a maintained, established library. Use auto-configuration, dependency injection, declarative APIs and generated mapping/accessors to eliminate boilerplate.
- Use framework or SDK facilities for retries, backoff, timeouts, scheduling and concurrency where they provide the required semantics. Establish whether coordination is local or distributed and whether idempotency covers a transaction or the complete HTTP command before choosing it.
- Add custom technical code only for a demonstrated gap. Explain the alternatives considered and the missing guarantee in the PR. Keep that code minimal and cohesive. Business decisions still belong in the domain, and existing repository constraints continue to apply.

## Java layout

- Follow the existing ChangePurchaseService layout as the reference for new and edited Java. Use four spaces. Keep the first declaration parameter beside the opening parenthesis and align subsequent parameters beneath it when wrapping.
- Keep the receiver and first call on the same line. Align subsequent chained-call dots vertically with the first dot when wrapping. Preserve intentional readable line breaks and keep short expressions on one line.
- Use the IntelliJ Java settings in .editorconfig. Spotless maintains unused imports, trailing whitespace and final newlines. Parameter/chain alignment is reviewed and applied by the IDE rather than enforced by spotlessCheck. Do not reintroduce a formatter that overrides this style or mass-reformat unrelated files.

## Keep methods cohesive

- Keep methods small and focused on one operation at one abstraction level. Application entry methods should expose named use-case steps. Extract a helper or collaborator when it gives a cohesive responsibility a clear name and reduces cognitive load.
- Keep business invariants in aggregates, use-case decisions in application services and persistence/SDK details in adapters. Preserve meaningful failure paths, atomicity, response replay and business timestamps during extraction.
- Prefer supported declarative framework capabilities for cross-cutting technical policies. Use composed annotations for reusable policies supported by the framework, with explicit semantics and verified proxy behavior. An annotation must not hide a new custom coordination engine or business workflow.
- Avoid arbitrary line limits, one-line helper proliferation and dependency containers that disguise responsibility counts. Follow the [service method assessment](docs/service-method-assessment.md) when reviewing orchestration methods.

## Finish honestly

Implement only the requested scope. Distinguish implemented behavior, test evidence and planned guarantees. Standalone simulator behavior does not prove a payment-app workflow.

Update requirements, affected docs/diagrams and durable memory in the same change. Add an ADR only for a material decision. Never fabricate test runs, benchmark results or implemented endpoints.

Run applicable checks from [CONTRIBUTING](CONTRIBUTING.md), including `python3 scripts/check-docs.py` for documentation changes. Report failures and unrun checks.

Keep memory short: current facts, constraints, sharp edges and next unimplemented boundary. No transcripts, personal machine paths, secrets or duplicate task diaries.
