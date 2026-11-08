# ADR-0005 — Cohesive parameter objects and a signature guardrail

Status: Accepted

## Context

Long positional argument lists obscured purchase changes, persistence and simulator phase operations. Merely wrapping arguments would not fix unclear concepts.

## Decision

Prefer 0–2 parameters; allow three when cohesive; enforce a maximum of three handwritten parameters through Checkstyle, with narrow justified exceptions.

Use concepts such as ChangeSessionPurchase, CommandMetadata, PurchaseRevision and CompletedSessionCommand. Preserve whole objects and derive redundant values.

## Consequences and alternatives

This is a project-specific guardrail inspired by parameter-object refactoring, not a numeric rule attributed to Fowler. Generated code, record components and Lombok constructors require cohesion review rather than mechanical wrapping.

Do not introduce ParameterBag or ServiceDependencies to bypass the rule. Keep framework binding exceptions explicit and documented.

Scope: REQ-ENG-003. See the [current guidelines](../parameter-guidelines.md) and [historical audit](../parameter-audit.md).
