package lab.payments.payment_session.domain;

import java.util.UUID;

public record PaymentSessionId(String value) {
    public static PaymentSessionId newId() {
        return new PaymentSessionId(UUID.randomUUID().toString());
    }
}
