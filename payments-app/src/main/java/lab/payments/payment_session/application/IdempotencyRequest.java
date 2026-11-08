package lab.payments.payment_session.application;

import lab.payments.payment_session.domain.MerchantId;

public record IdempotencyRequest(IdempotencyScope scope, String key, String fingerprint) {
    public MerchantId merchantId() {
        return scope.merchantId();
    }
}
