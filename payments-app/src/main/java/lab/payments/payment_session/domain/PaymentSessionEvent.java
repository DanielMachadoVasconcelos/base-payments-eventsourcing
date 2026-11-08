package lab.payments.payment_session.domain;

import java.time.Instant;

public record PaymentSessionEvent(
        long sequence,
        String type,
        Instant occurredAt,
        String commandId,
        PaymentSessionSnapshot snapshot) {}
