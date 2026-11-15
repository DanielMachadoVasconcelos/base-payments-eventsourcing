package lab.payments.payment_session.application;

import static lab.common.Json.fingerprint;

import java.util.Map;
import lab.common.BusinessClock;
import lab.payments.payment_session.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChangePurchaseService {
    private final PaymentSessionEventStore eventStore;
    private final PurchasePayloadStore purchasePayloadStore;
    private final PaymentSessionResultMapper resultMapper;
    private final BusinessClock businessClock;
    private final SessionExpirationPolicy expirationPolicy;

    public PaymentSessionView change(ChangePurchaseCommand command) {
        var context = command.context();
        var merchantId = context.identity().merchantId();
        var idempotency =
                new IdempotencyRequest(
                        new IdempotencyScope(merchantId, "CHANGE_PURCHASE", "v1"),
                        context.idempotencyKey(),
                        fingerprint(
                                Map.of(
                                        "purchase",
                                        command.purchase(),
                                        "expectedVersion",
                                        context.expectedVersion())));
        var previous = eventStore.previouslyCompletedResponse(idempotency);
        if (previous.isPresent()) {
            return resultMapper.fromStoredResult(previous.get(), merchantId);
        }
        var session =
                eventStore
                        .load(context.identity())
                        .orElseThrow(PaymentSessionNotFoundException::new);
        String payloadId = purchasePayloadStore.store(merchantId, command.purchase());
        var metadata = new CommandMetadata(CommandId.newId(), businessClock.instant());
        try {
            session.changePurchase(
                    new ChangeSessionPurchase(
                            PurchaseRevision.of(command.purchase(), payloadId),
                            context.expectedVersion(),
                            metadata),
                    expirationPolicy);
        } catch (PaymentSessionVersionMismatchException staleVersion) {
            // A same-key winner may have committed between the initial lookup and stream load.
            return eventStore
                    .previouslyCompletedResponse(idempotency)
                    .map(result -> resultMapper.fromStoredResult(result, merchantId))
                    .orElseThrow(() -> staleVersion);
        }
        var view = PaymentSessionView.from(session, command.purchase());
        var persistedResult =
                eventStore.append(
                        session,
                        new CompletedSessionCommand(
                                idempotency, resultMapper.toStoredResult(view, payloadId)));
        return resultMapper.fromStoredResult(persistedResult, merchantId);
    }
}
