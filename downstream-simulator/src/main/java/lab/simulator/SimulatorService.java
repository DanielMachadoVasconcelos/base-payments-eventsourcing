package lab.simulator;

import static lab.common.Json.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lab.common.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.*;

@Service
@RequiredArgsConstructor
public class SimulatorService {
    private final Dynamo dynamo;
    private final SimulatorFaultPolicy faultPolicy;
    private final SimulatorOperationProcessor operationProcessor;
    private final JsonDocumentCodec json;

    public Map<String, Object> result(SimulatorOperationKey key) {
        var result = dynamo.get(key.partitionKey(), "RESULT");
        if (result == null) {
            throw new PaymentNotFoundException(
                    "DOWNSTREAM_OUTCOME_NOT_FOUND", "No recorded outcome; this is not a rejection");
        }
        return result;
    }

    public Map<String, Object> execute(SimulateOperation operation) {
        String requestFingerprint = fingerprint(operation.request());
        var existing = dynamo.get(operation.key().partitionKey(), "RESULT");
        if (existing != null) {
            return verifyMatchingRequest(existing, requestFingerprint);
        }
        var fault = faultPolicy.consume(operation.target());
        faultPolicy.beforeProcessing(fault);
        var outcome = operationProcessor.prepare(operation, SimulationScenario.from(fault));
        var simulation = new PreparedSimulation(operation, outcome, requestFingerprint);
        try {
            commitOutcome(simulation);
        } catch (TransactionCanceledException exception) {
            var committed = dynamo.get(operation.key().partitionKey(), "RESULT");
            if (committed != null) {
                return verifyMatchingRequest(committed, requestFingerprint);
            }
            if (operation.key().phase() == SimulationPhase.ORDER) {
                throw new PaymentConflictException(
                        "ORDER_ALREADY_EXISTS", "Another order reservation won");
            }
            throw exception;
        }
        faultPolicy.afterCommit(fault);
        return json.toDocument(simulation.ledger());
    }

    private void commitOutcome(PreparedSimulation simulation) {
        var writes = new ArrayList<>(simulation.outcome().effectWrites());
        writes.add(
                newItem(
                        simulation.operation().key().partitionKey(),
                        "RESULT",
                        simulation.ledger()));
        writes.add(
                newItem(
                        simulation.sessionPartitionKey(),
                        "EFFECT#"
                                + simulation.operation().key().phase()
                                + "#"
                                + simulation.outcome().effectId(),
                        simulation.effect()));
        dynamo.transaction(writes, UUID.randomUUID().toString());
    }

    private TransactWriteItem newItem(String partitionKey, String sortKey, Object document) {
        return dynamo.put(
                Put.builder()
                        .tableName(dynamo.table)
                        .item(dynamo.item(partitionKey, sortKey, json.toDocument(document)))
                        .conditionExpression("attribute_not_exists(PK)")
                        .build());
    }

    private Map<String, Object> verifyMatchingRequest(
            Map<String, Object> result, String requestFingerprint) {
        if (!stringValue(result, "hash").equals(requestFingerprint)) {
            throw new PaymentConflictException(
                    "EXTERNAL_IDEMPOTENCY_CONFLICT", "Different request for same downstream key");
        }
        return result;
    }

    public void fault(String merchantId, Map<String, Object> configuration) {
        faultPolicy.configure(merchantId, configuration);
    }

    public List<Map<String, Object>> effects(String merchantId, String sessionId) {
        return dynamo.query("M#" + merchantId + "#S#" + sessionId, "EFFECT#").stream()
                .map(Dynamo::clean)
                .toList();
    }
}
