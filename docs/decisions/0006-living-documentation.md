# ADR-0006 — A short entry point and traceable living documentation

Status: Accepted

## Context

New engineers and AI agents have no access to the original conversation. A long speculative design can make planned guarantees look implemented.

## Decision

Keep README as the landing page; link architecture/Mermaid diagrams, current HTTP contracts, stable requirements with evidence and short ADRs. Keep agent instructions and working memory separate from the human introduction.

Provide a repository-local maintain-project-docs skill, contribution workflow, PR checklist and structural documentation checker. Update affected documents in the same change as behavior. Write repository documentation in English.

## Consequences and alternatives

Readers can progressively explore details without a transcript or duplicated full specifications. Requirement IDs and real test links make scope inspectable. Structural checks detect missing links/methods, not semantic truth; review and executed tests remain necessary.

Memory stores durable constraints, not diaries. Documentation-only fixes need no artificial requirement/ADR. Agent rules do not authorize additional implementation work or guarantee every tool will automatically load a skill.

Scope: REQ-ENG-004. Start at [CONTRIBUTING](../../CONTRIBUTING.md).
