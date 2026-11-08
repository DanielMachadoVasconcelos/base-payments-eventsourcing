package lab.payments.payment_session.entry;

import java.util.Map;

public record PurchaseResponse(
        long amountMinor,
        String currency,
        String merchantReference,
        Map<String, Object> attributes) {}
