package lab.payments.payment_session.application;

import java.util.Optional;
import lab.payments.payment_session.domain.*;

public interface PaymentSessionRepository {
    Optional<PaymentSessionAggregate> find(PaymentSessionIdentity identity);

    Optional<StoredSessionResult> idempotentResponse(IdempotencyRequest request);

    StoredSessionResult save(PaymentSessionAggregate aggregate, CompletedSessionCommand completion);
}
