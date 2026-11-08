# Architecture decisions

Short records explain **why**, while [requirements](../requirements.md) track what is implemented and tested. These initial records capture decisions already reflected in the code; they do not retroactively claim the entire roadmap is delivered.

| Decision | Trade-off |
| --- | --- |
| [0001 — Event-sourced sessions](0001-event-sourced-sessions.md) | Replayable facts and atomic command results; more explicit persistence |
| [0002 — Immutable chunked purchases](0002-chunked-purchases.md) | Large documents without bloated events; orphan cleanup and memory limits remain |
| [0003 — Domain boundaries and framework reuse](0003-domain-boundaries.md) | Explicit business types; generated mechanical mappings |
| [0004 — Compose and executable BDD](0004-compose-and-bdd.md) | One infrastructure definition; behavior evidence must actually execute |
| [0005 — Cohesive parameters](0005-cohesive-parameters.md) | A project guardrail, not a universal numeric law |
| [0006 — Living documentation](0006-living-documentation.md) | Short entry point, traceable requirements and repo-local agent workflow |

For a material new decision, copy the [template](template.md), allocate the next number and update this index. Accepted decisions are historical: add a superseding record if the choice changes. Correct factual mistakes transparently.

Do not record every refactoring as an ADR. Use one when invariants, ownership, persistence, compatibility or dependencies change.
