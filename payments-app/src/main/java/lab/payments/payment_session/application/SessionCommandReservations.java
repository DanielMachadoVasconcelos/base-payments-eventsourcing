package lab.payments.payment_session.application;

import java.util.Optional;

public interface SessionCommandReservations {
    Optional<SessionCommandReservation> acquire(IdempotencyRequest request);

    void release(SessionCommandReservation reservation);
}
