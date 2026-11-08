package lab.payments.payment_session.application;

import java.time.Instant;
import lab.payments.payment_session.domain.*;

public record PaymentSessionView(
        String sessionId, Purchase purchase, long version, String state, Instant expiresAt) {
    public static PaymentSessionView from(
            PaymentSessionAggregate aggregate, Purchase completePurchase) {
        return new PaymentSessionView(
                aggregate.id().value(),
                completePurchase,
                aggregate.version(),
                aggregate.state(),
                aggregate.expiresAt());
    }
}
