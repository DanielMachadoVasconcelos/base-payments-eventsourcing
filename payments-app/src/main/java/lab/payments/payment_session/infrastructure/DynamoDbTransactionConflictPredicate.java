package lab.payments.payment_session.infrastructure;

import java.lang.reflect.Method;
import org.springframework.resilience.retry.MethodRetryPredicate;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/** Selects transient item contention from the SDK's mixed transaction-cancellation reasons. */
public final class DynamoDbTransactionConflictPredicate implements MethodRetryPredicate {
    @Override
    public boolean shouldRetry(Method method, Throwable failure) {
        if (!(failure instanceof TransactionCanceledException cancelled)) return false;
        var reasons = cancelled.cancellationReasons();
        return reasons.stream().anyMatch(reason -> "TransactionConflict".equals(reason.code()))
                && reasons.stream()
                        .allMatch(
                                reason ->
                                        "None".equals(reason.code())
                                                || "TransactionConflict".equals(reason.code()));
    }
}
