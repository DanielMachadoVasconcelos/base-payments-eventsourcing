package lab.payments.payment_session.application;

import java.util.Map;
import lab.common.Json;
import lab.payments.payment_session.domain.MerchantId;

public record IdempotencyRequest(IdempotencyScope scope, String key, String fingerprint) {
    public static IdempotencyRequest forPurchaseChange(ChangePurchaseCommand command) {
        var context = command.context();
        return new IdempotencyRequest(
                new IdempotencyScope(context.identity().merchantId(), "CHANGE_PURCHASE", "v1"),
                context.idempotencyKey(),
                Json.fingerprint(
                        Map.of(
                                "purchase", command.purchase(),
                                "expectedVersion", context.expectedVersion())));
    }

    public MerchantId merchantId() {
        return scope.merchantId();
    }
}
