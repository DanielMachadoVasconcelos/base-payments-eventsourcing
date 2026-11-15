package lab.payments.payment_session.application;

import java.time.Duration;
import java.util.function.Function;
import lab.common.PaymentDomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SessionCommandCoordinator {
    private final SessionCommandReservations reservations;
    private final PaymentSessionEventStore eventStore;
    private final long waitNanos;
    private final Duration pollInterval;

    // Explicit collaborators and two independent wait settings.
    @SuppressWarnings("checkstyle:ParameterNumber")
    public SessionCommandCoordinator(
            SessionCommandReservations reservations,
            PaymentSessionEventStore eventStore,
            @Value("${lab.synchronous-wait-ms:3000}") long waitMillis,
            @Value("${lab.command-poll-ms:25}") long pollMillis) {
        this.reservations = reservations;
        this.eventStore = eventStore;
        if (waitMillis <= 0 || pollMillis <= 0)
            throw new IllegalArgumentException("Command wait settings must be positive");
        this.waitNanos = Duration.ofMillis(waitMillis).toNanos();
        this.pollInterval = Duration.ofMillis(pollMillis);
    }

    public StoredSessionResult execute(
            IdempotencyRequest request,
            Function<SessionCommandReservation, StoredSessionResult> operation) {
        long deadline = System.nanoTime() + waitNanos;
        do {
            var previous = eventStore.previouslyCompletedResponse(request);
            if (previous.isPresent()) return previous.get();
            var acquired = reservations.acquire(request);
            if (acquired.isPresent()) {
                var reservation = acquired.get();
                try {
                    return operation.apply(reservation);
                } catch (PaymentDomainException rejection) {
                    reservations.release(reservation);
                    throw rejection;
                }
            }
            awaitNextAttempt();
        } while (System.nanoTime() < deadline);
        throw new SessionCommandUnavailableException();
    }

    private void awaitNextAttempt() {
        try {
            Thread.sleep(pollInterval);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SessionCommandUnavailableException();
        }
    }
}
