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

## Reuse framework capabilities

Before implementing technical behavior, inspect the existing dependency graph, configuration and supported framework/SDK APIs. Prefer capabilities already available in the project. When a dependency is needed, prefer official compatible Spring Boot starters and auto-configuration, followed by maintained, established libraries.

Use declarative framework and SDK support for retries, backoff, timeouts, scheduling and concurrency when its documented guarantees satisfy the requirement. Use injected JSON mapping, MapStruct, records and Lombok for appropriate mechanical code generation. Avoid handwritten boilerplate and wrappers that merely duplicate library APIs.

Verify semantics before selecting a capability. Check local versus distributed coordination, retryable versus permanent errors, transaction versus HTTP-command idempotency, compatibility and persistence contracts. Configure retry budgets across layers to avoid multiplying attempts.

Custom technical code requires a concrete gap in existing capabilities. Describe the requirement, alternatives evaluated and missing guarantee in the PR, then implement the smallest cohesive adaptation. Preserve aggregate-owned business rules and the repository's Compose, dependency and storage constraints.

## Cohesive service methods

Each method should express one operation at one abstraction level. Keep application entry methods readable as named use-case steps. Extract helpers or collaborators where they reveal a cohesive responsibility and reduce cognitive load. Keep aggregate business decisions and adapter infrastructure details at their existing boundaries.

Prefer supported framework annotations for cross-cutting capabilities. Composed annotations are appropriate for genuinely reused technical policies when the framework supports them. Verify proxy boundaries, self-invocation and error matching. Keep business choices and consistency recovery explicit in their owning layer.

Use the [ChangePurchaseService assessment](docs/service-method-assessment.md) as the review example. Method size is a readability signal, without a fixed line-count target. Preserve behavior and persistence contracts when refactoring.

## Java layout

Use four-space indentation and the [ChangePurchaseService](payments-app/src/main/java/lab/payments/payment_session/application/ChangePurchaseService.java) layout as the reference. For multiline declarations, keep the first parameter next to the opening parenthesis and align following parameters beneath it. Keep the receiver and first method call together, then align the dots of subsequent wrapped calls vertically.

The IntelliJ Java properties in [.editorconfig](.editorconfig) enable multiline alignment and preserve intentional line breaks. The shared [IntelliJ profile](config/formatting/intellij-java.xml) supplies the matching Java wrapping and indentation policy with a 120-column right margin. Import it into IntelliJ Code Style or pass it to the native command-line formatter. Use the IDE's EditorConfig support for Java reformatting. Keep short expressions on one line and avoid reformatting unrelated code.

The production and test Java sources have been migrated with the native IntelliJ formatter. To format the same source roots again, use the formatter supplied by your IntelliJ installation:

```bash
format.sh -s config/formatting/intellij-java.xml -r -m '*.java' common/src payments-app/src downstream-simulator/src
```

Add `-d` for a dry-run validation. Run it from the repository root. The CLI needs an available IDE instance/configuration as described in [JetBrains documentation](https://www.jetbrains.com/help/idea/command-line-formatter.html). Existing intentional breaks are retained. Legacy hard wraps were normalized during the initial migration before restoring the reference class's deliberate breaks.

Spotless handles unused imports, trailing whitespace and final newlines. It preserves the authored parameter and chain layout. spotlessCheck does not validate alignment or wrapping. Full Google Java Format was removed because its fixed layout overrides the adopted style.

## BDD and verification

JUnit runs classes concurrently with two workers and methods sequentially. Each integration class must own its table and cleanup. Architecture checks enforce direct layer dependencies.

HTTP contract tests use RestTestClient with the full Spring context and a real isolated DynamoDB table through Compose. Use request and response records for valid JSON bodies, typed invalid values for validation cases and raw text for malformed JSON. The MVC-backed contract suite complements live-server concurrency and persistence tests. Live-server failure scenarios use RestTestClient with JdkClientHttpRequestFactory so automatic HTTP status retries cannot hide the first 503 response. Every JUnit test, including parameterized and architecture tests, must declare an explicit `@DisplayName("Should {outcome} When {context}")` and use the matching `should{Outcome}When{Context}` method name. Keep Given/When/Then sections inside behavior tests.

Executable behavior tests are JUnit tests; The method name and display name must both explain the outcome and context. A prose feature file alone does not prove a requirement.

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
