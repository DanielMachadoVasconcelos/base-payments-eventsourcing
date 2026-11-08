package lab.payments.payment_session.entry;

import lab.common.ApiValues;
import lab.payments.payment_session.domain.MerchantId;
import lab.payments.payment_session.domain.PaymentSessionId;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

/** Boundary validation used by the generated mapper; no persistence or business transitions. */
@Component
public class PaymentSessionInputConversions {
    public MerchantId toMerchantId(String merchantId) {
        return new MerchantId(ApiValues.merchant(merchantId));
    }

    public PaymentSessionId toSessionId(String sessionId) {
        return new PaymentSessionId(sessionId);
    }

    public long toAmountMinor(Object amountMinor) {
        return ApiValues.amountMinor(amountMinor);
    }

    public long toExpectedVersion(String versionHeader) {
        return ApiValues.expectedVersion(versionHeader);
    }

    @Named("validatedCurrency")
    public String validatedCurrency(String currency) {
        return ApiValues.currency(currency);
    }

    @Named("validatedIdempotencyKey")
    public String validatedIdempotencyKey(String idempotencyKey) {
        return ApiValues.idempotency(idempotencyKey);
    }
}
