package lab.payments.payment_session.domain;

import lab.common.PaymentNotFoundException;

public final class PaymentSessionNotFoundException extends PaymentNotFoundException {
    public PaymentSessionNotFoundException() {
        super("PAYMENT_SESSION_NOT_FOUND", "Payment session not found");
    }
}
