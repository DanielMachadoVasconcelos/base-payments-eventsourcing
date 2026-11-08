package lab.payments.payment_session.domain;

import java.time.Instant;
import java.util.Map;

/** Domain state; persistence maps this to the existing flat event snapshot schema. */
public record PaymentSessionSnapshot(
        PurchaseRevision revision, String state, SessionActivity activity, Instant retainUntil) {
    public record PurchaseSummary(long amountMinor, String currency, String merchantReference) {
        public static PurchaseSummary from(Purchase purchase) {
            return new PurchaseSummary(
                    purchase.amountMinor(),
                    purchase.currency(),
                    java.util.Objects.toString(purchase.merchantReference(), ""));
        }

        public Purchase withoutAttributes() {
            return new Purchase(amountMinor, currency, merchantReference, Map.of());
        }
    }
}
