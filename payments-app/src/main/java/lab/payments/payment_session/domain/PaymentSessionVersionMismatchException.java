package lab.payments.payment_session.domain;

import lab.common.PaymentConflictException;

public final class PaymentSessionVersionMismatchException extends PaymentConflictException {
    public PaymentSessionVersionMismatchException() {
        super(
                "PAYMENT_SESSION_VERSION_MISMATCH",
                "The payment session was changed by another request");
    }
}
