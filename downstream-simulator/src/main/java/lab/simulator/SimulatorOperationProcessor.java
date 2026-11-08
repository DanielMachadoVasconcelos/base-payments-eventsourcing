package lab.simulator;

import static lab.common.Json.*;

import java.util.List;
import java.util.Map;
import lab.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.*;

@Component
class SimulatorOperationProcessor {
    private final Dynamo dynamo;
    private final BusinessClock businessClock;
    private final JsonDocumentCodec json;
    private final long confirmationLifetimeMillis;

    // Infrastructure collaborators plus a configured issuer rule.
    @SuppressWarnings("checkstyle:ParameterNumber")
    SimulatorOperationProcessor(
            Dynamo dynamo,
            BusinessClock businessClock,
            JsonDocumentCodec json,
            @Value("${lab.issuer-confirmation-ms:14400000}") long confirmationLifetimeMillis) {
        this.dynamo = dynamo;
        this.businessClock = businessClock;
        this.json = json;
        this.confirmationLifetimeMillis = confirmationLifetimeMillis;
    }

    record AuthorizationSimulation(SimulateOperation operation, SimulationScenario scenario) {}

    record ConfirmationSimulation(SimulateOperation operation, SimulationScenario scenario) {}

    record OrderSimulation(SimulateOperation operation, SimulationScenario scenario) {}

    SimulatedOutcome prepare(SimulateOperation operation, SimulationScenario scenario) {
        return switch (operation.key().phase()) {
            case AUTHORIZE -> authorize(new AuthorizationSimulation(operation, scenario));
            case CONFIRM -> confirm(new ConfirmationSimulation(operation, scenario));
            case ORDER -> createOrder(new OrderSimulation(operation, scenario));
            case WEBHOOK -> receiveWebhook(operation.request());
        };
    }

    private SimulatedOutcome authorize(AuthorizationSimulation command) {
        var operation = command.operation();
        String authorizationId = stringValue(operation.request(), "authorizationId");
        String status = command.scenario() == SimulationScenario.REJECT ? "DECLINED" : "ACCEPTED";
        var authorization =
                SimulatorDocuments.Authorization.builder()
                        .status(status)
                        .issuerAuthorizationId(authorizationId)
                        .acceptedAt(businessClock.millis())
                        .issuerExpiresAt(businessClock.millis() + confirmationLifetimeMillis)
                        .purchase(objectValue(operation.request(), "purchase"))
                        .build();
        var response = json.toDocument(authorization);
        if (command.scenario() == SimulationScenario.REJECT) {
            return new SimulatedOutcome(authorizationId, 200, response);
        }
        var write =
                newItem(
                        "M#" + operation.key().merchantId() + "#AUTH#" + authorizationId,
                        "STATE",
                        authorization);
        return new SimulatedOutcome(authorizationId, 200, response, List.of(write));
    }

    private SimulatedOutcome confirm(ConfirmationSimulation command) {
        var operation = command.operation();
        String authorizationId = stringValue(operation.request(), "authorizationId");
        var authorization =
                dynamo.get(
                        "M#" + operation.key().merchantId() + "#AUTH#" + authorizationId, "STATE");
        boolean cannotConfirm =
                command.scenario() == SimulationScenario.REJECT
                        || authorization == null
                        || businessClock.millis() >= longValue(authorization, "issuerExpiresAt");
        var response =
                new SimulatorDocuments.Confirmation(
                        cannotConfirm ? "REJECTED" : "CONFIRMED",
                        authorizationId,
                        businessClock.millis());
        return new SimulatedOutcome(
                stringValue(operation.request(), "operationId"),
                cannotConfirm ? 422 : 200,
                json.toDocument(response));
    }

    private SimulatedOutcome createOrder(OrderSimulation command) {
        var operation = command.operation();
        String partitionKey =
                "M#" + operation.key().merchantId() + "#ORDER#" + operation.target().sessionId();
        String orderId = stringValue(operation.request(), "orderId");
        var previous = dynamo.get(partitionKey, "ORDER");
        int status = command.scenario() == SimulationScenario.REJECT ? 422 : 201;
        if (previous != null) {
            ensureSamePurchase(previous, operation.request());
            return new SimulatedOutcome(orderId, status, Dynamo.clean(previous));
        }
        var order =
                SimulatorDocuments.Order.builder()
                        .status(
                                command.scenario() == SimulationScenario.REJECT
                                        ? "REJECTED"
                                        : "CREATED")
                        .orderId(orderId)
                        .sessionId(operation.target().sessionId())
                        .authorizationId(stringValue(operation.request(), "authorizationId"))
                        .purchase(objectValue(operation.request(), "purchase"))
                        .build();
        var response = json.toDocument(order);
        if (command.scenario() == SimulationScenario.REJECT) {
            return new SimulatedOutcome(orderId, status, response);
        }
        return new SimulatedOutcome(
                orderId, status, response, List.of(newItem(partitionKey, "ORDER", order)));
    }

    private void ensureSamePurchase(Map<String, Object> previous, Map<String, Object> request) {
        if (!fingerprint(objectValue(previous, "purchase"))
                .equals(fingerprint(objectValue(request, "purchase")))) {
            throw new PaymentConflictException(
                    "ORDER_ALREADY_EXISTS", "A different order already exists for this session");
        }
    }

    private SimulatedOutcome receiveWebhook(Map<String, Object> request) {
        String deliveryId = stringValue(request, "deliveryId");
        return new SimulatedOutcome(
                deliveryId,
                200,
                json.toDocument(
                        new SimulatorDocuments.Webhook(
                                "DELIVERED", deliveryId, businessClock.millis())));
    }

    private TransactWriteItem newItem(String partitionKey, String sortKey, Object document) {
        return dynamo.put(
                Put.builder()
                        .tableName(dynamo.table)
                        .item(dynamo.item(partitionKey, sortKey, json.toDocument(document)))
                        .conditionExpression("attribute_not_exists(PK)")
                        .build());
    }
}
