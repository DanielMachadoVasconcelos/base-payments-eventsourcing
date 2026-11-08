package lab.payments.payment_session.domain;

public record OpenPaymentSession(
        PaymentSessionIdentity identity, PurchaseRevision purchase, CommandMetadata metadata) {}
