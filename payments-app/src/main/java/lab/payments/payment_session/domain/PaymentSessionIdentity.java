package lab.payments.payment_session.domain;

public record PaymentSessionIdentity(MerchantId merchantId, PaymentSessionId sessionId) {}
