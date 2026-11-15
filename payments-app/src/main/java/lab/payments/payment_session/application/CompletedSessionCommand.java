package lab.payments.payment_session.application;

public record CompletedSessionCommand(
        IdempotencyRequest idempotency,
        StoredSessionResult response,
        SessionCommandReservation reservation) {
    public CompletedSessionCommand(IdempotencyRequest idempotency, StoredSessionResult response) {
        this(idempotency, response, null);
    }

    public CompletedSessionCommand {
        if (reservation != null && !reservation.request().equals(idempotency)) {
            throw new IllegalArgumentException("Reservation belongs to a different command");
        }
    }
}
