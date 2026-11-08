---
name: maintain-project-docs
description: Keep this repository's English documentation aligned with new or changed requirements, behavior, API contracts and architecture decisions. Use when implementing or reviewing such changes, adding test evidence, or refreshing the README, diagrams, requirements catalogue or agent memory. Distinguish implemented scope from planned payment guarantees.
---

# Maintain project documentation

Maintain a short, accurate entry point and a traceable design record. Do not implement extra product features merely to close documentation gaps.

All paths below are relative to the repository root. From this file, the root is three directories up.

## 1. Establish the actual change

Read root AGENTS.md and README.md, docs/agent-memory.md and the affected sections of docs/requirements.md. Inspect relevant source and executable tests before describing behavior.

Read docs/architecture.md, docs/api.md, docs/parameter-guidelines.md or relevant docs/decisions records only when the change touches them.

Do not infer implementation from a plan, a registered task, a prose feature file or a simulator-only test.

## 2. Keep requirements traceable

For new behavior, allocate the next unused stable REQ family ID in docs/requirements.md. For changed behavior, update the existing requirement; never renumber/reuse old IDs. Retire or supersede abandoned requirements explicitly.

Use the existing section format:

- Heading: requirement ID and meaningful title.
- Status: Verified, Adopted or Planned; preserve obsolete IDs as Retired or Superseded.
- Acceptance: observable Given/When/Then behavior, with boundaries and exclusions.
- Implementation: repository-relative source link.
- Evidence for Verified: a link labelled with an actual executable Java test method name, including parentheses.

Verified means implemented with executable evidence, not that every possible failure was tested. Run relevant tests and report actual outcomes. If evidence does not exist, retain Planned for the unproven target and describe any implemented subset separately; do not invent a passing test or silently downgrade a previously proven rule.

Adopted is for engineering policy/configuration, not a shortcut to claim business verification. Cosmetic edits need no new requirement.

## 3. Update only affected explanations

- README.md: public scope, setup, navigation or prominent limitations.
- docs/architecture.md: responsibility boundaries, domain types, storage protocol and diagrams.
- docs/api.md: actual routes, headers, examples, status codes and retry behavior.
- docs/parameter-guidelines.md: coding conventions, exceptions and cohesive types.
- docs/agent-memory.md: durable facts, traps and current unimplemented boundary; keep it short.
- docs/decisions: material changes to invariants, ownership, persistence, compatibility or dependencies.

For an ADR, use docs/decisions/template.md, allocate the next number and update its index. Record alternatives/trade-offs. Supersede accepted decisions rather than silently rewriting their rationale.

Use native Mermaid with real type/component names. Explicitly label proposed flows. Keep the landing page short and avoid copying the same detailed specification into several files.

## 4. Verify and hand over

Run:

```bash
python3 scripts/check-docs.py
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Run the relevant Gradle checks described in CONTRIBUTING.md for code/behavior changes. Check the diagrams visually in a Mermaid-capable viewer when changing them; structural Markdown checks are not a rendering test.

Before finishing, check:

- Could a new engineer understand the current system without the chat?
- Are planned guarantees clearly separated from proven behavior?
- Do links and test names resolve without local machine paths?
- Have changed contracts and stored formats been preserved or explicitly addressed?
- Are test failures and unrun checks disclosed?

Summarize changed documentation, requirement IDs, decisions and actual verification. Do not claim that rules/skills automatically enforce semantic accuracy.
