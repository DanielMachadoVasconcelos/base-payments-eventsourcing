package lab.payments.payment_session.application;

/** Unique ownership of one scoped command key until a wall-clock deadline. */
public record SessionCommandReservation(IdempotencyRequest request, String owner, long expiresAt) {}
