package lab.payments.payment_session.domain;

import lab.common.PaymentExpiredException;

public final class PaymentSessionExpiredException extends PaymentExpiredException {
    public PaymentSessionExpiredException() {
        super("PAYMENT_SESSION_EXPIRED", "The payment session expired");
    }
}
