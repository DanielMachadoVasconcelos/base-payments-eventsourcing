package lab.payments.payment_session.application;

import lab.common.PaymentDomainException;

public final class SessionCommandUnavailableException extends PaymentDomainException {
    public SessionCommandUnavailableException() {
        super(
                503,
                "SESSION_COMMAND_IN_PROGRESS",
                "The command could not complete within the wait deadline; retry the same Idempotency-Key");
    }
}
