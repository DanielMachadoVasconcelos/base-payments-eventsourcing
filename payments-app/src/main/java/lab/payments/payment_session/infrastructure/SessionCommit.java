package lab.payments.payment_session.infrastructure;

import static lab.common.Json.sha256;

import lab.payments.payment_session.application.CompletedSessionCommand;
import lab.payments.payment_session.application.IdempotencyRequest;
import lab.payments.payment_session.domain.PaymentSessionAggregate;

/** Request-local prepared commit. Addresses are derived rather than supplied independently. */
record SessionCommit(PaymentSessionAggregate session, CompletedSessionCommand completion) {
    SessionCommit {
        if (!session.merchantId().equals(completion.idempotency().merchantId())
                || !session.id().value().equals(completion.response().sessionId())
                || !session.purchasePayloadId().equals(completion.response().purchasePayloadId())) {
            throw new IllegalArgumentException(
                    "Completion does not belong to this session revision");
        }
    }

    String sessionPartitionKey() {
        return "M#" + session.merchantId().value() + "#S#" + session.id().value();
    }

    String idempotencyPartitionKey() {
        return idempotencyPartitionKey(completion.idempotency());
    }

    static String idempotencyPartitionKey(IdempotencyRequest request) {
        var scope = request.scope();
        return "M#"
                + scope.merchantId().value()
                + "#IDEMP#"
                + sha256(scope.operation() + ":" + scope.apiVersion() + ":" + request.key());
    }

    String payloadPartitionKey() {
        return "M#" + session.merchantId().value() + "#PAYLOAD#" + session.purchasePayloadId();
    }

    long expectedVersion() {
        return session.version() - session.changes().size();
    }
}
