package lab.payments.payment_session.entry;

import java.util.Map;

public record CreatePaymentSessionRequest(
        Object amountMinor,
        String currency,
        String merchantReference,
        Map<String, Object> attributes) {}
