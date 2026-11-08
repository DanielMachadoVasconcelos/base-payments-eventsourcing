# ADR-0003 — Domain language with framework-assisted boundaries

Status: Accepted

## Context

The code should make session decisions readable without making engineers hand-maintain mechanical conversions or understand AWS details inside an aggregate.

## Decision

Use Controller → Mapper → Command → Service → Aggregate → Event Store → Repository as the responsibility spine. Application services orchestrate; domain commands express intentions; aggregates enforce transitions.

Use MapStruct for boundary mappings, records for immutable data, Lombok for appropriate boilerplate and Spring-injected ObjectMapper for serialization. Prefer existing SDK request builders over duplicate infrastructure DSLs. Use named business failures, not LabException.

## Consequences and alternatives

Explicit types clarify ownership and make pure aggregate tests possible. Mapping code is generated and must not be edited. Field changes can fail compilation instead of silently dropping target fields.

Do not wrap every SDK/framework type or invent generic context bags. Domain exceptions still inherit HTTP-status-bearing common exceptions; full error/transport separation is not complete.

Scope: REQ-ENG-002 and [architecture](../architecture.md).
