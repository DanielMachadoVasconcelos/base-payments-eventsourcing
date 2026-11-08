package lab.payments.payment_session.entry;

import java.util.Map;

public record ChangePurchaseRequest(
        Object amountMinor,
        String currency,
        String merchantReference,
        Map<String, Object> attributes) {}
