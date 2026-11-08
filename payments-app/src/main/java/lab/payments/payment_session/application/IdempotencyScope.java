package lab.payments.payment_session.application;

import lab.payments.payment_session.domain.MerchantId;

public record IdempotencyScope(MerchantId merchantId, String operation, String apiVersion) {}
