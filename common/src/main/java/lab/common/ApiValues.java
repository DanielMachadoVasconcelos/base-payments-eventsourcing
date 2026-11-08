package lab.common;

import static lab.common.Json.*;

import java.math.BigDecimal;
import java.util.*;

public final class ApiValues {
    private ApiValues() {}

    public static String merchant(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}"))
            throw new PaymentValidationException(
                    "INVALID_MERCHANT", "X-Merchant-Id must contain 1–64 letters, digits, _ or -");
        return value;
    }

    public static String idempotency(String value) {
        if (value == null || value.isBlank() || value.length() > 256)
            throw new PaymentValidationException(
                    "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain 1–256 characters");
        return value;
    }

    public static long expectedVersion(String versionHeader) {
        try {
            return Long.parseLong(versionHeader.replace("\"", ""));
        } catch (RuntimeException exception) {
            throw new PaymentValidationException(
                    "INVALID_ETAG", "If-Match must be a numeric session version");
        }
    }

    public static long amountMinor(Object value) {
        long amount;
        try {
            amount = new BigDecimal(Objects.toString(value)).longValueExact();
        } catch (RuntimeException e) {
            throw new PaymentValidationException(
                    "INVALID_AMOUNT", "amountMinor must be a positive integer");
        }
        if (amount <= 0)
            throw new PaymentValidationException("INVALID_AMOUNT", "amountMinor must be positive");
        return amount;
    }

    public static String currency(String currency) {
        if (currency == null || !currency.matches("[A-Z]{3}"))
            throw new PaymentValidationException(
                    "INVALID_CURRENCY", "currency must be an uppercase ISO currency code");
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw new PaymentValidationException("INVALID_CURRENCY", "Unknown currency");
        }
        return currency;
    }
}
