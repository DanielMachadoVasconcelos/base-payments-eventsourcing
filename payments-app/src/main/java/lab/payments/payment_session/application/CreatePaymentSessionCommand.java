package lab.payments.payment_session.application;

import lab.payments.payment_session.domain.*;

public record CreatePaymentSessionCommand(
        MerchantId merchantId, Purchase purchase, String idempotencyKey) {}
