package lab.payments.payment_session.application;

import lab.payments.payment_session.domain.*;

public interface PurchasePayloadStore {
    String store(MerchantId merchantId, Purchase purchase);

    Purchase load(MerchantId merchantId, String payloadId);
}
