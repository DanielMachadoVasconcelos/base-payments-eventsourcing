package lab.payments.payment_session.domain;

import java.time.Instant;

public record SessionActivity(
        Instant createdAt, Instant lastBusinessActivityAt, Instant expiresAt) {
    public static SessionActivity start(Instant occurredAt, SessionExpirationPolicy policy) {
        return new SessionActivity(occurredAt, occurredAt, policy.expiresAfter(occurredAt));
    }

    public SessionActivity advance(Instant occurredAt, SessionExpirationPolicy policy) {
        return new SessionActivity(createdAt, occurredAt, policy.expiresAfter(occurredAt));
    }
}
