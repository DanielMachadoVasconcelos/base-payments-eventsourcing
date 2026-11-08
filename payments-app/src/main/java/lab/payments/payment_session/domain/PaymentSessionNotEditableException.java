package lab.payments.payment_session.domain;

import lab.common.PaymentConflictException;

public final class PaymentSessionNotEditableException extends PaymentConflictException {
    public PaymentSessionNotEditableException() {
        super(
                "PAYMENT_SESSION_NOT_EDITABLE",
                "The payment session cannot be changed in its current state");
    }
}
