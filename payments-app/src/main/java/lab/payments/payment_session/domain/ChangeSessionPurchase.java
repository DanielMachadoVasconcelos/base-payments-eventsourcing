package lab.payments.payment_session.domain;

public record ChangeSessionPurchase(
        PurchaseRevision replacement, long expectedVersion, CommandMetadata metadata) {}
