package lab.payments.payment_session.application;

import java.util.Optional;
import lab.payments.payment_session.domain.*;

public interface PaymentSessionEventStore {
    Optional<PaymentSessionAggregate> load(PaymentSessionIdentity identity);

    Optional<StoredSessionResult> previouslyCompletedResponse(IdempotencyRequest request);

    StoredSessionResult append(
            PaymentSessionAggregate aggregate, CompletedSessionCommand completion);
}
