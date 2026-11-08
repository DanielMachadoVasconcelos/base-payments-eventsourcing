package lab.payments.payment_session.infrastructure;

import java.time.Instant;
import lab.payments.payment_session.application.StoredSessionResult;
import lab.payments.payment_session.domain.*;
import lombok.Builder;

/** Storage-only DTOs preserve the original flat JSON contracts. */
public final class PaymentSessionDocuments {
    private PaymentSessionDocuments() {}

    @Builder
    public record SnapshotDocument(
            PaymentSessionSnapshot.PurchaseSummary purchase,
            String purchasePayloadId,
            String state,
            Instant createdAt,
            Instant lastBusinessActivityAt,
            Instant expiresAt,
            Instant retainUntil) {
        public static SnapshotDocument from(PaymentSessionSnapshot snapshot) {
            return builder()
                    .purchase(snapshot.revision().summary())
                    .purchasePayloadId(snapshot.revision().payloadId().value())
                    .state(snapshot.state())
                    .createdAt(snapshot.activity().createdAt())
                    .lastBusinessActivityAt(snapshot.activity().lastBusinessActivityAt())
                    .expiresAt(snapshot.activity().expiresAt())
                    .retainUntil(snapshot.retainUntil())
                    .build();
        }

        public PaymentSessionSnapshot toDomain() {
            return new PaymentSessionSnapshot(
                    new PurchaseRevision(purchase, new PurchasePayloadId(purchasePayloadId)), state,
                    new SessionActivity(createdAt, lastBusinessActivityAt, expiresAt), retainUntil);
        }
    }

    @Builder
    public record EventDocument(
            long sequence,
            String eventType,
            Instant occurredAt,
            String commandId,
            SnapshotDocument snapshot) {
        public static EventDocument from(PaymentSessionEvent event) {
            return builder()
                    .sequence(event.sequence())
                    .eventType(event.type())
                    .occurredAt(event.occurredAt())
                    .commandId(event.commandId())
                    .snapshot(SnapshotDocument.from(event.snapshot()))
                    .build();
        }

        public PaymentSessionEvent toDomain() {
            return new PaymentSessionEvent(
                    sequence, eventType, occurredAt, commandId, snapshot.toDomain());
        }
    }

    @Builder
    public record HeadDocument(
            String merchantId,
            String sessionId,
            long version,
            String state,
            PaymentSessionSnapshot.PurchaseSummary purchase,
            String purchasePayloadId,
            Instant createdAt,
            Instant lastBusinessActivityAt,
            Instant expiresAt,
            Instant retainUntil,
            String activeOperationId,
            long pendingDeliveries) {
        public static HeadDocument from(PaymentSessionAggregate session) {
            return builder()
                    .merchantId(session.merchantId().value())
                    .sessionId(session.id().value())
                    .version(session.version())
                    .state(session.state())
                    .purchase(PaymentSessionSnapshot.PurchaseSummary.from(session.purchase()))
                    .purchasePayloadId(session.purchasePayloadId())
                    .createdAt(session.createdAt())
                    .lastBusinessActivityAt(session.lastBusinessActivityAt())
                    .expiresAt(session.expiresAt())
                    .retainUntil(session.retainUntil())
                    .activeOperationId("")
                    .pendingDeliveries(0)
                    .build();
        }
    }

    public record StreamMetadata(long version, String lifecycle) {}

    public record IdempotencyDocument(
            String requestHash, StoredSessionResult response, String sessionId, long completedAt) {}

    public record ReverseReference(String targetPk, String targetSk, String ownerSession) {}
}
