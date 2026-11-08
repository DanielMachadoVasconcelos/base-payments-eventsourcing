package lab.payments.payment_session.entry;

import java.time.Instant;

public record PaymentSessionResponse(
        String sessionId,
        PurchaseResponse purchase,
        long version,
        String state,
        Instant expiresAt) {}
