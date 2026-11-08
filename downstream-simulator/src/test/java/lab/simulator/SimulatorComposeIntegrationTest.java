package lab.simulator;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.UUID;
import lab.common.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;

@Tag("compose")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimulatorComposeIntegrationTest {
    private static final String TABLE_NAME =
            "simulator_parameters_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String MERCHANT_ID = "simulator-test";
    @Autowired private SimulatorService simulator;
    @Autowired private JsonDocumentCodec json;
    @Autowired private Dynamo dynamo;

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("lab.table", () -> TABLE_NAME);
    }

    @AfterAll
    static void cleanup(@Autowired Dynamo dynamo) {
        dynamo.client.deleteTable(request -> request.tableName(TABLE_NAME));
    }

    @Test
    void givenLostResponseAfterCommitWhenRetriedThenOnlyOneEffectExists() {
        var operation = authorization();
        simulator.fault(
                MERCHANT_ID,
                Map.of(
                        "sessionId",
                        operation.target().sessionId(),
                        "phase",
                        "AUTHORIZE",
                        "mode",
                        "FAIL_AFTER"));
        assertThatThrownBy(() -> simulator.execute(operation))
                .isInstanceOf(PaymentPersistenceFailure.class);
        var recovered = simulator.execute(operation);
        assertThat(Json.objectValue(recovered, "response").get("status")).isEqualTo("ACCEPTED");
        assertThat(simulator.effects(MERCHANT_ID, operation.target().sessionId())).hasSize(1);
    }

    @Test
    void givenFailureBeforeCommitWhenRetriedThenNoUnrecordedEffectIsDuplicated() {
        var operation = authorization();
        simulator.fault(
                MERCHANT_ID,
                Map.of(
                        "sessionId",
                        operation.target().sessionId(),
                        "phase",
                        "AUTHORIZE",
                        "mode",
                        "FAIL_BEFORE"));
        assertThatThrownBy(() -> simulator.execute(operation))
                .isInstanceOf(PaymentPersistenceFailure.class);
        assertThat(simulator.effects(MERCHANT_ID, operation.target().sessionId())).isEmpty();
        assertThatThrownBy(() -> simulator.result(operation.key()))
                .isInstanceOf(PaymentNotFoundException.class);
        var faultItem =
                dynamo.client
                        .getItem(
                                request ->
                                        request.tableName(TABLE_NAME)
                                                .key(
                                                        Dynamo.key(
                                                                "M#"
                                                                        + MERCHANT_ID
                                                                        + "#FAULT#"
                                                                        + operation
                                                                                .target()
                                                                                .sessionId(),
                                                                "AUTHORIZE")))
                        .item();
        assertThat(json.deserializeObject(faultItem.get("data").s()))
                .doesNotContainKeys("_pk", "_sk");
        simulator.execute(operation);
        assertThat(simulator.effects(MERCHANT_ID, operation.target().sessionId())).hasSize(1);
    }

    @Test
    void givenAnOrderWhenAnotherPurchaseUsesTheSessionThenThereIsStillOnlyOneOrder() {
        String sessionId = UUID.randomUUID().toString();
        var original = order(sessionId, 10000);
        assertThat(Json.longValue(simulator.execute(original), "httpStatus")).isEqualTo(201);
        assertThatThrownBy(() -> simulator.execute(order(sessionId, 15000)))
                .isInstanceOf(PaymentConflictException.class);
        assertThat(simulator.effects(MERCHANT_ID, sessionId)).hasSize(1);
    }

    @Test
    void givenRejectionScenarioWhenPreparingAuthorizationThenNoAuthorizationStateIsWritten() {
        var operation = authorization();
        simulator.fault(
                MERCHANT_ID,
                Map.of(
                        "sessionId",
                        operation.target().sessionId(),
                        "phase",
                        "AUTHORIZE",
                        "mode",
                        "REJECT"));
        var rejected = simulator.execute(operation);
        assertThat(Json.objectValue(rejected, "response").get("status")).isEqualTo("DECLINED");
        assertThat(Json.objectValue(simulator.execute(operation), "response"))
                .isEqualTo(Json.objectValue(rejected, "response"));
        assertThat(simulator.effects(MERCHANT_ID, operation.target().sessionId())).hasSize(1);
    }

    @Test
    void givenTheExistingHttpContractWhenPostingAndLookingUpThenBothResolveTheSameOperation()
            throws Exception {
        var operation = authorization();
        var client = HttpClient.newHttpClient();
        var post =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/sim/AUTHORIZE"))
                        .header("X-Merchant-Id", MERCHANT_ID)
                        .header("Idempotency-Key", operation.key().idempotencyKey())
                        .header("Content-Type", "application/json")
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        json.serialize(operation.request())))
                        .build();
        var response = client.send(post, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.deserializeObject(response.body()).get("status")).isEqualTo("ACCEPTED");
        var get =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://localhost:"
                                                + port
                                                + "/sim/results/AUTHORIZE/"
                                                + operation.key().idempotencyKey()))
                        .header("X-Merchant-Id", MERCHANT_ID)
                        .GET()
                        .build();
        var lookup = client.send(get, HttpResponse.BodyHandlers.ofString());
        assertThat(lookup.statusCode()).isEqualTo(200);
        assertThat(Json.objectValue(json.deserializeObject(lookup.body()), "response"))
                .isEqualTo(json.deserializeObject(response.body()));
    }

    private SimulateOperation authorization() {
        var key =
                new SimulatorOperationKey(
                        MERCHANT_ID, SimulationPhase.AUTHORIZE, UUID.randomUUID().toString());
        return new SimulateOperation(
                key,
                Map.of(
                        "sessionId",
                        UUID.randomUUID().toString(),
                        "authorizationId",
                        UUID.randomUUID().toString(),
                        "purchase",
                        Map.of("amountMinor", 10000, "currency", "BRL")));
    }

    private SimulateOperation order(String sessionId, long amountMinor) {
        var key =
                new SimulatorOperationKey(
                        MERCHANT_ID, SimulationPhase.ORDER, UUID.randomUUID().toString());
        return new SimulateOperation(
                key,
                Map.of(
                        "sessionId",
                        sessionId,
                        "orderId",
                        UUID.randomUUID().toString(),
                        "authorizationId",
                        UUID.randomUUID().toString(),
                        "purchase",
                        Map.of("amountMinor", amountMinor, "currency", "BRL")));
    }
}
