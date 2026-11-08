package lab.payments.payment_session.application;

import static lab.common.Json.fingerprint;

import java.util.Map;
import lab.common.BusinessClock;
import lab.payments.payment_session.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreatePaymentSessionService {
    private final PaymentSessionEventStore eventStore;
    private final PurchasePayloadStore purchasePayloadStore;
    private final PaymentSessionResultMapper resultMapper;
    private final BusinessClock businessClock;
    private final SessionExpirationPolicy expirationPolicy;

    public PaymentSessionView create(CreatePaymentSessionCommand command) {
        var idempotency =
                new IdempotencyRequest(
                        new IdempotencyScope(command.merchantId(), "CREATE_SESSION", "v1"),
                        command.idempotencyKey(),
                        fingerprint(Map.of("purchase", command.purchase())));
        var previous = eventStore.previouslyCompletedResponse(idempotency);
        if (previous.isPresent()) {
            return resultMapper.fromStoredResult(previous.get(), command.merchantId());
        }
        var identity = new PaymentSessionIdentity(command.merchantId(), PaymentSessionId.newId());
        String payloadId = purchasePayloadStore.store(command.merchantId(), command.purchase());
        var metadata = new CommandMetadata(CommandId.newId(), businessClock.instant());
        var session =
                PaymentSessionAggregate.create(
                        new OpenPaymentSession(
                                identity,
                                PurchaseRevision.of(command.purchase(), payloadId),
                                metadata),
                        expirationPolicy);
        var view = PaymentSessionView.from(session, command.purchase());
        var persistedResult =
                eventStore.append(
                        session,
                        new CompletedSessionCommand(
                                idempotency, resultMapper.toStoredResult(view, payloadId)));
        return resultMapper.fromStoredResult(persistedResult, command.merchantId());
    }
}
