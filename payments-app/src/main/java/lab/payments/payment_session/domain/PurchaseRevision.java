package lab.payments.payment_session.domain;

public record PurchaseRevision(
        PaymentSessionSnapshot.PurchaseSummary summary, PurchasePayloadId payloadId) {
    public static PurchaseRevision of(Purchase purchase, String payloadId) {
        return new PurchaseRevision(
                PaymentSessionSnapshot.PurchaseSummary.from(purchase),
                new PurchasePayloadId(payloadId));
    }
}
