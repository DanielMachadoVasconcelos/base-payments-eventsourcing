package lab.payments.payment_session.infrastructure;

import static lab.common.Json.sha256;
import static lab.payments.payment_session.infrastructure.PaymentSessionDocuments.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lab.common.Dynamo;
import lab.common.JsonDocumentCodec;
import lab.common.PaymentConflictException;
import lab.payments.payment_session.application.*;
import lab.payments.payment_session.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.model.*;

@Repository
@RequiredArgsConstructor
public class DynamoDbPaymentSessionRepository implements PaymentSessionRepository {
    private static final String ITEM_MUST_NOT_EXIST = "attribute_not_exists(PK)";
    private final Dynamo dynamo;
    private final PaymentSessionDocumentMapper documentMapper;
    private final JsonDocumentCodec json;

    @Override
    public Optional<PaymentSessionAggregate> find(PaymentSessionIdentity identity) {
        String partitionKey =
                "M#" + identity.merchantId().value() + "#S#" + identity.sessionId().value();
        var documents = dynamo.query(partitionKey, "EVT#");
        if (documents.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                PaymentSessionAggregate.rehydrate(
                        identity, documents.stream().map(documentMapper::toEvent).toList()));
    }

    @Override
    public Optional<StoredSessionResult> idempotentResponse(IdempotencyRequest request) {
        var saved = dynamo.get(SessionCommit.idempotencyPartitionKey(request), "RESULT");
        if (saved == null) {
            return Optional.empty();
        }
        var document = json.fromDocument(saved, IdempotencyDocument.class);
        verifyFingerprint(document, request);
        return Optional.of(document.response());
    }

    @Override
    public StoredSessionResult save(
            PaymentSessionAggregate session, CompletedSessionCommand completion) {
        var commit = new SessionCommit(session, completion);
        var result = commit(commit);
        session.markChangesPersisted();
        return result;
    }

    private StoredSessionResult commit(SessionCommit commit) {
        try {
            dynamo.transaction(writes(commit), UUID.randomUUID().toString());
            return commit.completion().response();
        } catch (TransactionCanceledException exception) {
            var saved = dynamo.get(commit.idempotencyPartitionKey(), "RESULT");
            if (saved == null) {
                throw exception;
            }
            var result = json.fromDocument(saved, IdempotencyDocument.class);
            verifyFingerprint(result, commit.completion().idempotency());
            return result.response();
        }
    }

    private void verifyFingerprint(IdempotencyDocument result, IdempotencyRequest request) {
        if (!result.requestHash().equals(request.fingerprint())) {
            throw new PaymentConflictException(
                    "IDEMPOTENCY_KEY_CONFLICT",
                    "The Idempotency-Key was already used with a different request");
        }
    }

    private List<TransactWriteItem> writes(SessionCommit commit) {
        var writes = new ArrayList<>(eventWrites(commit));
        writes.addAll(projectionWrites(commit));
        writes.add(completedResultWrite(commit));
        writes.addAll(referenceWrites(commit));
        return writes;
    }

    private List<TransactWriteItem> eventWrites(SessionCommit commit) {
        return commit.session().changes().stream()
                .map(
                        event ->
                                newItem(
                                        commit.sessionPartitionKey(),
                                        String.format("EVT#%020d", event.sequence()),
                                        EventDocument.from(event)))
                .toList();
    }

    private List<TransactWriteItem> projectionWrites(SessionCommit commit) {
        return List.of(
                projectionWrite(commit, "HEAD", HeadDocument.from(commit.session())),
                projectionWrite(
                        commit, "META", new StreamMetadata(commit.session().version(), "LIVE")));
    }

    private TransactWriteItem projectionWrite(
            SessionCommit commit, String sortKey, Object document) {
        var put =
                Put.builder()
                        .tableName(dynamo.table)
                        .item(
                                dynamo.item(
                                        commit.sessionPartitionKey(),
                                        sortKey,
                                        json.toDocument(document)));
        if (commit.expectedVersion() == 0) {
            put.conditionExpression(ITEM_MUST_NOT_EXIST);
        } else {
            put.conditionExpression("version = :previous")
                    .expressionAttributeValues(
                            Map.of(":previous", Dynamo.numberAttribute(commit.expectedVersion())));
        }
        return dynamo.put(put.build());
    }

    private TransactWriteItem completedResultWrite(SessionCommit commit) {
        var result =
                new IdempotencyDocument(
                        commit.completion().idempotency().fingerprint(),
                        commit.completion().response(),
                        commit.session().id().value(),
                        System.currentTimeMillis());
        return newItem(commit.idempotencyPartitionKey(), "RESULT", result);
    }

    private List<TransactWriteItem> referenceWrites(SessionCommit commit) {
        return List.of(
                newItem(
                        commit.sessionPartitionKey(),
                        "REF#PAYLOAD#" + sha256(commit.session().purchasePayloadId()),
                        new ReverseReference(
                                commit.payloadPartitionKey(),
                                "MANIFEST",
                                commit.sessionPartitionKey())),
                newItem(
                        commit.sessionPartitionKey(),
                        "REF#IDEMP#" + sha256(commit.idempotencyPartitionKey()),
                        new ReverseReference(
                                commit.idempotencyPartitionKey(),
                                "RESULT",
                                commit.sessionPartitionKey())));
    }

    private TransactWriteItem newItem(String partitionKey, String sortKey, Object document) {
        return dynamo.put(
                Put.builder()
                        .tableName(dynamo.table)
                        .item(dynamo.item(partitionKey, sortKey, json.toDocument(document)))
                        .conditionExpression(ITEM_MUST_NOT_EXIST)
                        .build());
    }
}
