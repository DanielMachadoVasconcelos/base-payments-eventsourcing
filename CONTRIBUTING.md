# Contributing

Keep the next reader oriented. Code, tests, requirements and design explanations should describe the same system.

## Before changing behavior

1. Read the [README](README.md), relevant [requirement](docs/requirements.md) and [architecture boundary](docs/architecture.md).
2. Give a new requirement a stable ID, or update the existing one. Write an observable acceptance condition before implementing it.
3. Add a failing Given/When/Then example at the appropriate level. Keep domain decisions independent of transport and persistence.
4. Implement the smallest cohesive change and run the relevant checks.
5. Update the requirement's status and evidence. Update affected diagrams, HTTP examples and decisions in the same change.

Do not create a requirement or ADR for a spelling fix or mechanical formatting change. If documentation is unaffected, explain why in the review.

## Where code belongs

| Responsibility | Location |
| --- | --- |
| HTTP binding and MapStruct mapping | `payments-app/.../payment_session/entry` |
| Use-case coordination, application commands and ports | `payments-app/.../payment_session/application` |
| Aggregate decisions, domain commands, events and value objects | `payments-app/.../payment_session/domain` |
| DynamoDB documents, adapters and transaction assembly | `payments-app/.../payment_session/infrastructure` |
| Independent durable simulated external effects | `downstream-simulator` |
| Shared technical support, not a shared business model | `common/src/main/java` |

Preserve the [request/replay spine](docs/architecture.md). Services coordinate; aggregates decide; repositories persist. Avoid introducing empty layers merely to complete a diagram.

Use descriptive business names and specific failures such as PaymentSessionExpiredException. No LabException, generic parameter bags or request data stored in singleton fields.

Use records for cohesive immutable data, MapStruct for structural boundary mappings and Lombok where it removes mechanical boilerplate. Inject framework-managed JSON mappers. Prefer SDK/framework types over custom wrappers with no domain meaning. Never edit generated mapper sources.

Follow the [parameter rules](docs/parameter-guidelines.md): prefer 0–2 parameters, allow three when cohesive, and justify locally suppressed exceptions.

## BDD and verification

JUnit runs classes concurrently with two workers and methods sequentially. Each integration class must own its table and cleanup. Architecture checks enforce direct layer dependencies.

HTTP contract tests use RestTestClient with the full Spring context and a real isolated DynamoDB table through Compose. Use request and response records for valid JSON bodies, typed invalid values for validation cases and raw text for malformed JSON. The MVC-backed contract suite complements live-server concurrency and persistence tests. Use Should ... When ... display names and Given/When/Then sections.

Executable behavior tests are JUnit tests; Given/When/Then names or display names should explain the rule. A prose feature file alone does not prove a requirement.

```bash
./gradlew check
./gradlew integrationTest
python3 scripts/check-docs.py
```

Compose is the single infrastructure definition. Do not add Testcontainers or an independent test-only dependency stack. Keep test tables isolated. Do not remove the developer's Compose volume during tests.

An application API guarantee needs application-level evidence: simulator-only assertions are insufficient. A restart or multi-process claim needs a test that actually stops/restarts processes or runs multiple instances.

## Documentation contract

- Write repository documentation and newly added explanations in English.
- [Requirements](docs/requirements.md) own scope/evidence, [ADRs](docs/decisions/README.md) own rationale, and [agent memory](docs/agent-memory.md) is a short handover—not another specification.
- Use repository-relative links, native Mermaid diagrams and real source names. Label planned flows explicitly.
- Add an ADR for a material change in invariants, ownership, persistence, compatibility or dependencies. Use the [template](docs/decisions/template.md); supersede an accepted decision instead of silently rewriting history.
- Never mark a requirement verified because code exists, a task is registered or a scenario is described. Link an executable test and report what actually ran.
- Keep old IDs stable. Retire or supersede a requirement rather than reusing its ID.
- Keep the README a short landing page; put deep details behind links.

The repo-local [maintain-project-docs skill](.agents/skills/maintain-project-docs/SKILL.md) applies this workflow to AI-assisted changes. The [GitHub documentation workflow](.github/workflows/documentation.yml) runs structural checks on pushes and pull requests, including source-only changes that might rename a linked test. Human review remains responsible for semantic accuracy; these checks cannot determine whether a requirement's acceptance condition is sufficiently tested.
