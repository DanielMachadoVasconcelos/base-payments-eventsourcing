package lab.payments.payment_session.infrastructure;

import java.util.Optional;
import lab.payments.payment_session.application.*;
import lab.payments.payment_session.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DynamoDbPaymentSessionEventStore implements PaymentSessionEventStore {
    private final PaymentSessionRepository repository;

    @Override
    public Optional<PaymentSessionAggregate> load(PaymentSessionIdentity identity) {
        return repository.find(identity);
    }

    @Override
    public Optional<StoredSessionResult> previouslyCompletedResponse(IdempotencyRequest request) {
        return repository.idempotentResponse(request);
    }

    @Override
    public StoredSessionResult append(
            PaymentSessionAggregate aggregate, CompletedSessionCommand completion) {
        return repository.save(aggregate, completion);
    }
}
