# ADR-0002 — Immutable chunked purchase payloads

Status: Accepted

## Context

A merchant purchase can exceed DynamoDB's per-item limit. Embedding full attributes in every event, HEAD and idempotency record would repeat large documents and hit that limit.

## Decision

Store each full purchase separately as an immutable manifest and ordered chunks. Events and response descriptors retain the purchase summary and payload identity. Publish COMPLETE only after all chunks are stored; validate count/checksum when reading.

Use 200 KiB raw chunks with Base64/metadata headroom. Upload before the session transaction; reference the completed payload when committing session state.

## Consequences and alternatives

This avoids a fixed single-item cap on purchase attributes without introducing another storage service in this DynamoDB experiment. It is not unlimited storage in one item, a cross-upload atomic transaction, or a streaming HTTP implementation.

Full documents are buffered in memory; summaries still contain metadata such as merchantReference. Interrupted/losing uploads may be orphaned. Streaming ingestion, orphan cleanup and safe purge require further work. Object storage would be a separate architectural option, not an existing dependency.

Scope/evidence: REQ-DATA-001; planned cleanup: REQ-DEL-001 in the [catalogue](../requirements.md).
