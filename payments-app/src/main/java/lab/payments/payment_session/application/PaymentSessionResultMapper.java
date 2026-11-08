package lab.payments.payment_session.application;

import lab.payments.payment_session.domain.MerchantId;
import lab.payments.payment_session.domain.PaymentSessionSnapshot.PurchaseSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentSessionResultMapper {
    private final PurchasePayloadStore purchasePayloadStore;

    public StoredSessionResult toStoredResult(PaymentSessionView session, String payloadId) {
        return StoredSessionResult.builder()
                .sessionId(session.sessionId())
                .purchasePayloadId(payloadId)
                .purchase(PurchaseSummary.from(session.purchase()))
                .version(session.version())
                .state(session.state())
                .expiresAt(session.expiresAt())
                .build();
    }

    public PaymentSessionView fromStoredResult(StoredSessionResult result, MerchantId merchantId) {
        var purchase = purchasePayloadStore.load(merchantId, result.purchasePayloadId());
        return new PaymentSessionView(
                result.sessionId(), purchase, result.version(), result.state(), result.expiresAt());
    }
}
