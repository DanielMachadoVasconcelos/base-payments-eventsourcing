package lab.payments.payment_session.application;

import lab.payments.payment_session.domain.PaymentSessionIdentity;

public record SessionWriteContext(
        PaymentSessionIdentity identity, String idempotencyKey, long expectedVersion) {}
