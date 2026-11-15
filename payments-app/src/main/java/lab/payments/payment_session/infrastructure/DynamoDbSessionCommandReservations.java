package lab.payments.payment_session.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lab.common.Dynamo;
import lab.payments.payment_session.application.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.model.*;

@Repository
public class DynamoDbSessionCommandReservations implements SessionCommandReservations {
    private final Dynamo dynamo;
    private final long leaseMillis;

    public DynamoDbSessionCommandReservations(
            Dynamo dynamo, @Value("${lab.lease-ms:30000}") long leaseMillis) {
        if (leaseMillis <= 0) throw new IllegalArgumentException("Command lease must be positive");
        this.dynamo = dynamo;
        this.leaseMillis = leaseMillis;
    }

    @Override
    public Optional<SessionCommandReservation> acquire(IdempotencyRequest request) {
        long now = System.currentTimeMillis();
        var reservation =
                new SessionCommandReservation(
                        request, UUID.randomUUID().toString(), now + leaseMillis);
        String partition = SessionCommit.idempotencyPartitionKey(request);
        var put =
                Put.builder()
                        .tableName(dynamo.table)
                        .item(
                                dynamo.item(
                                        partition,
                                        "PROCESSING",
                                        Map.of(
                                                "leaseOwner",
                                                reservation.owner(),
                                                "leaseUntil",
                                                reservation.expiresAt())))
                        .conditionExpression("attribute_not_exists(PK) OR leaseUntil <= :now")
                        .expressionAttributeValues(Map.of(":now", Dynamo.numberAttribute(now)))
                        .build();
        var completed =
                ConditionCheck.builder()
                        .tableName(dynamo.table)
                        .key(Dynamo.key(partition, "RESULT"))
                        .conditionExpression("attribute_not_exists(PK)")
                        .build();
        try {
            dynamo.transaction(
                    List.of(
                            dynamo.put(put),
                            TransactWriteItem.builder().conditionCheck(completed).build()),
                    UUID.randomUUID().toString());
            return Optional.of(reservation);
        } catch (TransactionCanceledException conflict) {
            // Only contention and failed ownership/result conditions mean "try again".
            if (conflict.cancellationReasons().stream()
                    .anyMatch(
                            reason ->
                                    !List.of(
                                                    "None",
                                                    "ConditionalCheckFailed",
                                                    "TransactionConflict")
                                            .contains(reason.code()))) {
                throw conflict;
            }
            return Optional.empty();
        }
    }

    @Override
    public void release(SessionCommandReservation reservation) {
        try {
            dynamo.client.deleteItem(
                    builder ->
                            builder.tableName(dynamo.table)
                                    .key(
                                            Dynamo.key(
                                                    SessionCommit.idempotencyPartitionKey(
                                                            reservation.request()),
                                                    "PROCESSING"))
                                    .conditionExpression("leaseOwner = :owner")
                                    .expressionAttributeValues(
                                            Map.of(
                                                    ":owner",
                                                    Dynamo.stringAttribute(reservation.owner()))));
        } catch (ConditionalCheckFailedException lostOwnership) {
            // An expired owner must never remove a successor's reservation.
        }
    }
}
