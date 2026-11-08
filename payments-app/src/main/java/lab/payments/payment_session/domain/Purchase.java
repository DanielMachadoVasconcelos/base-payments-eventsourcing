package lab.payments.payment_session.domain;

import java.util.Map;

public record Purchase(
        long amountMinor,
        String currency,
        String merchantReference,
        Map<String, Object> attributes) {}
