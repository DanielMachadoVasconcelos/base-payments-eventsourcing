package lab.simulator;

import static lab.common.Json.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lab.common.Dynamo;
import lab.common.PaymentPersistenceFailure;
import lab.common.PaymentValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/** Persists and consumes the failures configured for a simulator operation. */
@Component
@RequiredArgsConstructor
public class SimulatorFaultPolicy {
    private final Dynamo dynamo;

    public void configure(String merchantId, Map<String, Object> configuration) {
        if (!Set.of("AUTHORIZE", "CONFIRM", "ORDER", "WEBHOOK")
                .contains(stringValue(configuration, "phase"))) {
            throw new PaymentValidationException("INVALID_PHASE", "Invalid phase");
        }
        if (!Set.of("REJECT", "BEFORE_DELAY", "AFTER_DELAY", "FAIL_BEFORE", "FAIL_AFTER")
                .contains(stringValue(configuration, "mode"))) {
            throw new PaymentValidationException("INVALID_FAULT", "Unknown mode");
        }
        var storedFault = copyDocument(configuration);
        storedFault.put("version", 1L);
        storedFault.putIfAbsent("remaining", 1L);
        storedFault.putIfAbsent("delayMs", 3000L);
        String partitionKey =
                "M#" + merchantId + "#FAULT#" + stringValue(configuration, "sessionId");
        dynamo.client.putItem(
                builder ->
                        builder.tableName(dynamo.table)
                                .item(
                                        dynamo.item(
                                                partitionKey,
                                                stringValue(configuration, "phase"),
                                                storedFault)));
    }

    public Map<String, Object> consume(SimulationTarget target) {
        String merchantId = target.merchantId();
        String sessionId = target.sessionId();
        String phase = target.phase().name();
        String partitionKey = "M#" + merchantId + "#FAULT#" + sessionId;
        for (int attempt = 0; attempt < 5; attempt++) {
            var configuredFault = dynamo.get(partitionKey, phase);
            if (configuredFault == null || longValue(configuredFault, "remaining") <= 0) {
                return Map.of();
            }
            long previousVersion = longValue(configuredFault, "version");
            configuredFault.put("remaining", longValue(configuredFault, "remaining") - 1);
            configuredFault.put("version", previousVersion + 1);
            var write =
                    dynamo.put(
                            Put.builder()
                                    .tableName(dynamo.table)
                                    .item(
                                            dynamo.item(
                                                    partitionKey,
                                                    phase,
                                                    Dynamo.clean(configuredFault)))
                                    .conditionExpression("version = :v")
                                    .expressionAttributeValues(
                                            Map.of(":v", Dynamo.numberAttribute(previousVersion)))
                                    .build());
            try {
                dynamo.transaction(List.of(write), UUID.randomUUID().toString());
                return configuredFault;
            } catch (TransactionCanceledException ignored) {
                // Another caller consumed this version. Re-read before trying again.
            }
        }
        return Map.of();
    }

    public void beforeProcessing(Map<String, Object> fault) {
        if ("BEFORE_DELAY".equals(stringValue(fault, "mode"))) {
            delay(longValue(fault, "delayMs"));
        }
        if ("FAIL_BEFORE".equals(stringValue(fault, "mode"))) {
            throw new PaymentPersistenceFailure("Simulated failure before side effect");
        }
    }

    public void afterCommit(Map<String, Object> fault) {
        if ("AFTER_DELAY".equals(stringValue(fault, "mode"))) {
            delay(longValue(fault, "delayMs"));
        }
        if ("FAIL_AFTER".equals(stringValue(fault, "mode"))) {
            throw new PaymentPersistenceFailure(
                    "Simulated response loss after durable side effect");
        }
    }

    private static void delay(long delayMillis) {
        try {
            Thread.sleep(Math.min(Math.max(delayMillis, 0), 60_000));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
