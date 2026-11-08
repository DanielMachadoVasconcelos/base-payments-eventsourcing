package lab.payments.payment_session.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Owns session decisions. Commands carry intent; policies provide explicit domain rules. */
public final class PaymentSessionAggregate {
    private final PaymentSessionIdentity identity;
    private final List<PaymentSessionEvent> uncommittedEvents = new ArrayList<>();
    private long version;
    private PaymentSessionSnapshot snapshot;

    private PaymentSessionAggregate(PaymentSessionIdentity identity) {
        this.identity = identity;
    }

    public static PaymentSessionAggregate create(
            OpenPaymentSession command, SessionExpirationPolicy expirationPolicy) {
        var session = new PaymentSessionAggregate(command.identity());
        var activity = SessionActivity.start(command.metadata().occurredAt(), expirationPolicy);
        var snapshot =
                new PaymentSessionSnapshot(
                        command.purchase(), "OPEN", activity, activity.expiresAt());
        session.record(
                new PaymentSessionEvent(
                        1,
                        "PaymentSessionCreated",
                        command.metadata().occurredAt(),
                        command.metadata().commandId().value(),
                        snapshot));
        return session;
    }

    public static PaymentSessionAggregate rehydrate(
            PaymentSessionIdentity identity, List<PaymentSessionEvent> events) {
        if (events.isEmpty()) {
            throw new PaymentSessionNotFoundException();
        }
        var session = new PaymentSessionAggregate(identity);
        events.forEach(session::apply);
        return session;
    }

    public void changePurchase(
            ChangeSessionPurchase command, SessionExpirationPolicy expirationPolicy) {
        ensureVersionMatches(command.expectedVersion());
        ensurePurchaseCanBeChanged(command.metadata().occurredAt());
        var activity =
                snapshot.activity().advance(command.metadata().occurredAt(), expirationPolicy);
        var changed =
                new PaymentSessionSnapshot(
                        command.replacement(), "OPEN", activity, activity.expiresAt());
        record(
                new PaymentSessionEvent(
                        version + 1,
                        "PurchaseChanged",
                        command.metadata().occurredAt(),
                        command.metadata().commandId().value(),
                        changed));
    }

    private void ensureVersionMatches(long expectedVersion) {
        if (expectedVersion != version) {
            throw new PaymentSessionVersionMismatchException();
        }
    }

    private void ensurePurchaseCanBeChanged(Instant occurredAt) {
        if (!"OPEN".equals(state())) {
            throw new PaymentSessionNotEditableException();
        }
        if (!occurredAt.isBefore(expiresAt())) {
            throw new PaymentSessionExpiredException();
        }
    }

    private void record(PaymentSessionEvent event) {
        apply(event);
        uncommittedEvents.add(event);
    }

    private void apply(PaymentSessionEvent event) {
        snapshot = event.snapshot();
        version = event.sequence();
    }

    public List<PaymentSessionEvent> changes() {
        return List.copyOf(uncommittedEvents);
    }

    public void markChangesPersisted() {
        uncommittedEvents.clear();
    }

    public PaymentSessionIdentity identity() {
        return identity;
    }

    public MerchantId merchantId() {
        return identity.merchantId();
    }

    public PaymentSessionId id() {
        return identity.sessionId();
    }

    public Purchase purchase() {
        return snapshot.revision().summary().withoutAttributes();
    }

    public String purchasePayloadId() {
        return snapshot.revision().payloadId().value();
    }

    public long version() {
        return version;
    }

    public String state() {
        return snapshot.state();
    }

    public Instant createdAt() {
        return snapshot.activity().createdAt();
    }

    public Instant lastBusinessActivityAt() {
        return snapshot.activity().lastBusinessActivityAt();
    }

    public Instant expiresAt() {
        return snapshot.activity().expiresAt();
    }

    public Instant retainUntil() {
        return snapshot.retainUntil();
    }
}
