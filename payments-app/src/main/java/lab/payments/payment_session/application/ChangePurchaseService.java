package lab.payments.payment_session.application;

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
        var idempotency = IdempotencyRequest.forPurchaseChange(command);
        var result =
                eventStore
                        .previouslyCompletedResponse(idempotency)
                        .orElseGet(() -> replacePurchase(command, idempotency));
        return resultMapper.fromStoredResult(result, idempotency.merchantId());
    }

    private StoredSessionResult replacePurchase(
            ChangePurchaseCommand command, IdempotencyRequest idempotency) {
        var session =
                eventStore
                        .load(command.context().identity())
                        .orElseThrow(PaymentSessionNotFoundException::new);
        var payloadId = purchasePayloadStore.store(idempotency.merchantId(), command.purchase());
        var revision = PurchaseRevision.of(command.purchase(), payloadId);
        try {
            applyPurchaseChange(session, command, revision);
        } catch (PaymentSessionVersionMismatchException staleVersion) {
            return resolveConcurrentResult(idempotency, staleVersion);
        }
        return completePurchaseChange(session, command.purchase(), idempotency);
    }

    private void applyPurchaseChange(
            PaymentSessionAggregate session,
            ChangePurchaseCommand command,
            PurchaseRevision revision) {
        var metadata = new CommandMetadata(CommandId.newId(), businessClock.instant());
        var change =
                new ChangeSessionPurchase(revision, command.context().expectedVersion(), metadata);
        session.changePurchase(change, expirationPolicy);
    }

    private StoredSessionResult resolveConcurrentResult(
            IdempotencyRequest idempotency, PaymentSessionVersionMismatchException staleVersion) {
        // A same-key winner may commit between the initial result lookup and stream load.
        return eventStore.previouslyCompletedResponse(idempotency).orElseThrow(() -> staleVersion);
    }

    private StoredSessionResult completePurchaseChange(
            PaymentSessionAggregate session, Purchase purchase, IdempotencyRequest idempotency) {
        var view = PaymentSessionView.from(session, purchase);
        var response = resultMapper.toStoredResult(view, session.purchasePayloadId());
        return eventStore.append(session, new CompletedSessionCommand(idempotency, response));
    }
}
