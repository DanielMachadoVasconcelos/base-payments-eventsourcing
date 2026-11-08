package lab.payments.payment_session.application;

import java.time.Instant;
import lab.payments.payment_session.domain.PaymentSessionSnapshot.PurchaseSummary;
import lombok.Builder;

/** Original response metadata; the full immutable purchase is loaded by payload reference. */
@Builder
public record StoredSessionResult(
        String sessionId,
        String purchasePayloadId,
        PurchaseSummary purchase,
        long version,
        String state,
        Instant expiresAt) {}
