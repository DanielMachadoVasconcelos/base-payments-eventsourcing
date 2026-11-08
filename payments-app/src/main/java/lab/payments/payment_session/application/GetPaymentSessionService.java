package lab.payments.payment_session.application;

import lab.payments.payment_session.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPaymentSessionService {
    private final PaymentSessionEventStore eventStore;
    private final PurchasePayloadStore purchasePayloadStore;

    public PaymentSessionView get(PaymentSessionIdentity identity) {
        var session = eventStore.load(identity).orElseThrow(PaymentSessionNotFoundException::new);
        return PaymentSessionView.from(
                session,
                purchasePayloadStore.load(identity.merchantId(), session.purchasePayloadId()));
    }
}
