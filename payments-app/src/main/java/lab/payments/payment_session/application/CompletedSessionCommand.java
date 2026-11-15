package lab.payments.payment_session.application;

public record CompletedSessionCommand(
        IdempotencyRequest idempotency, StoredSessionResult response) {}
