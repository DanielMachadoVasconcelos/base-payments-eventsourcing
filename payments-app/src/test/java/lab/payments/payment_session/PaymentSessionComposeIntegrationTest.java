package lab.payments.payment_session;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import lab.common.*;
import lab.payments.payment_session.application.*;
import lab.payments.payment_session.domain.*;
import lab.payments.payment_session.infrastructure.PaymentSessionDocumentMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import tools.jackson.databind.ObjectMapper;

@Tag("compose")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentSessionComposeIntegrationTest {
    private static final String TABLE_NAME =
            "payments_parameters_test_" + UUID.randomUUID().toString().replace("-", "");
    private final HttpClient httpClient = HttpClient.newHttpClient();
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JsonDocumentCodec json;
    @Autowired private PaymentSessionDocumentMapper documentMapper;
    @Autowired private Dynamo dynamo;
    @Autowired private PaymentSessionEventStore eventStore;
    @Autowired private PurchasePayloadStore purchasePayloadStore;
    @Autowired private PaymentSessionResultMapper resultMapper;

    @Value("${local.server.port}")
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("lab.table", () -> TABLE_NAME);
        properties.add("lab.workers-enabled", () -> false);
    }

    @AfterAll
    static void cleanup(@Autowired Dynamo dynamo) {
        dynamo.client.deleteTable(request -> request.tableName(TABLE_NAME));
    }

    @Test
    void givenLargePurchaseWhenReadAndRetriedThenItsEntireResponseIsPreserved() throws Exception {
        String body =
                objectMapper.writeValueAsString(
                        Map.of(
                                "amountMinor",
                                10000,
                                "currency",
                                "BRL",
                                "merchantReference",
                                "large-cart",
                                "attributes",
                                Map.of("notes", "a".repeat(650000))));
        var request = creation(UUID.randomUUID().toString(), body);
        var created = send(request);
        assertThat(created.statusCode()).isEqualTo(201);
        var original = objectMapper.readTree(created.body());
        var repeated = send(request);
        var loaded =
                send(request("/v1/sessions/" + original.get("sessionId").asText()).GET().build());
        assertThat(repeated.statusCode()).isEqualTo(201);
        assertThat(loaded.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(repeated.body())).isEqualTo(original);
        assertThat(objectMapper.readTree(loaded.body())).isEqualTo(original);
        assertThat(original.get("purchase").get("attributes").get("notes").asText())
                .hasSize(650000);
    }

    @Test
    void givenChangedPurchaseWhenCreationIsRetriedThenItsOriginalResultIsReplayed()
            throws Exception {
        var createRequest =
                creation(
                        UUID.randomUUID().toString(),
                        "{\"amountMinor\":10000,\"currency\":\"BRL\"}");
        var created = send(createRequest);
        assertThat(created.statusCode()).isEqualTo(201);
        var original = objectMapper.readTree(created.body());
        String path = "/v1/sessions/" + original.get("sessionId").asText();
        var update =
                request(path)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("If-Match", "\"1\"")
                        .PUT(
                                HttpRequest.BodyPublishers.ofString(
                                        "{\"amountMinor\":15000,\"currency\":\"BRL\"}"))
                        .build();
        var changed = send(update);
        assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(send(update).body()))
                .isEqualTo(objectMapper.readTree(changed.body()));
        assertThat(objectMapper.readTree(send(createRequest).body())).isEqualTo(original);
        var current = send(request(path).GET().build());
        assertThat(objectMapper.readTree(current.body()).get("version").asLong()).isEqualTo(2);
        assertThat(
                        objectMapper
                                .readTree(current.body())
                                .get("purchase")
                                .get("amountMinor")
                                .asLong())
                .isEqualTo(15000);
        assertThat(current.headers().firstValue("ETag")).contains("\"2\"");
    }

    @Test
    void givenExistingEventDocumentWhenReplayedThenItsStorageSchemaIsPreserved() {
        var original =
                json.deserializeObject(
                        """
                {"sequence":1,"eventType":"PaymentSessionCreated","occurredAt":"2026-10-18T12:00:00Z",
                 "commandId":"original-command","snapshot":{
                 "purchase":{"amountMinor":10000,"currency":"BRL","merchantReference":"original-cart"},
                 "purchasePayloadId":"original-payload","state":"OPEN",
                 "createdAt":"2026-10-18T12:00:00Z","lastBusinessActivityAt":"2026-10-18T12:00:00Z",
                 "expiresAt":"2026-10-20T12:00:00Z","retainUntil":"2026-10-20T12:00:00Z"}}
                """);
        var event = documentMapper.toEvent(original);
        assertThat(event.snapshot().revision().summary().amountMinor()).isEqualTo(10000);
        assertThat(event.snapshot().revision().payloadId().value()).isEqualTo("original-payload");
        assertThat(objectMapper.readTree(json.serialize(documentMapper.toEventDocument(event))))
                .isEqualTo(objectMapper.readTree(json.serialize(original)));
    }

    @Test
    void givenConcurrentIdenticalCommandsWhenCompletedThenEverySuccessNamesTheSameSession()
            throws Exception {
        var request =
                creation(
                        UUID.randomUUID().toString(),
                        "{\"amountMinor\":10000,\"currency\":\"BRL\"}");
        var calls =
                IntStream.range(0, 12)
                        .mapToObj(
                                ignored ->
                                        httpClient.sendAsync(
                                                request, HttpResponse.BodyHandlers.ofString()))
                        .toList();
        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();
        var finalResponse = send(request);
        assertThat(finalResponse.statusCode()).isEqualTo(201);
        var original = objectMapper.readTree(finalResponse.body());
        for (var call : calls) {
            var response = call.join();
            assertThat(response.statusCode()).isIn(201, 503);
            if (response.statusCode() == 201) {
                assertThat(objectMapper.readTree(response.body())).isEqualTo(original);
            }
        }
        String partitionKey = "M#readability-test#S#" + original.get("sessionId").asText();
        assertThat(dynamo.query(partitionKey, "EVT#")).hasSize(1);
        assertThat(dynamo.get(partitionKey, "HEAD").get("version")).isEqualTo(1L);
    }

    @Test
    void givenSameKeyWithDifferentContentThenTheOriginalSessionIsNotOverwritten() throws Exception {
        String key = UUID.randomUUID().toString();
        var original = send(creation(key, "{\"amountMinor\":10000,\"currency\":\"BRL\"}"));
        var conflict = send(creation(key, "{\"amountMinor\":15000,\"currency\":\"BRL\"}"));
        assertThat(original.statusCode()).isEqualTo(201);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(conflict.body()).get("code").asText())
                .isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void givenMalformedEtagWhenUpdatingThenBoundaryValidationRemainsUnchanged() throws Exception {
        var update =
                request("/v1/sessions/any-session")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("If-Match", "invalid-version")
                        .PUT(
                                HttpRequest.BodyPublishers.ofString(
                                        "{\"amountMinor\":15000,\"currency\":\"BRL\"}"))
                        .build();
        var response = send(update);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("code").asText())
                .isEqualTo("INVALID_ETAG");
    }

    @Test
    void givenAStaleCommitWhenItsConditionFailsThenNoPartialEventOrIdempotencyResultIsWritten()
            throws Exception {
        var created =
                send(
                        creation(
                                UUID.randomUUID().toString(),
                                "{\"amountMinor\":10000,\"currency\":\"BRL\"}"));
        assertThat(created.statusCode()).isEqualTo(201);
        String sessionId = objectMapper.readTree(created.body()).get("sessionId").asText();
        var identity =
                new PaymentSessionIdentity(
                        new MerchantId("readability-test"), new PaymentSessionId(sessionId));
        var stale = eventStore.load(identity).orElseThrow();
        var winningUpdate =
                request("/v1/sessions/" + sessionId)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("If-Match", "\"1\"")
                        .PUT(
                                HttpRequest.BodyPublishers.ofString(
                                        "{\"amountMinor\":15000,\"currency\":\"BRL\"}"))
                        .build();
        assertThat(send(winningUpdate).statusCode()).isEqualTo(200);

        var replacement = new Purchase(20000, "BRL", "", Map.of());
        String payloadId = purchasePayloadStore.store(identity.merchantId(), replacement);
        var metadata =
                new CommandMetadata(
                        new CommandId("stale-command"), stale.createdAt().plusSeconds(1));
        stale.changePurchase(
                new ChangeSessionPurchase(PurchaseRevision.of(replacement, payloadId), 1, metadata),
                new SessionExpirationPolicy(Duration.ofHours(48)));
        String key = UUID.randomUUID().toString();
        var idempotency =
                new IdempotencyRequest(
                        new IdempotencyScope(identity.merchantId(), "CHANGE_PURCHASE", "v1"),
                        key,
                        "stale-fingerprint");
        var completion =
                new CompletedSessionCommand(
                        idempotency,
                        resultMapper.toStoredResult(
                                PaymentSessionView.from(stale, replacement), payloadId));

        assertThatThrownBy(() -> eventStore.append(stale, completion))
                .isInstanceOf(TransactionCanceledException.class);
        String partitionKey = "M#readability-test#S#" + sessionId;
        assertThat(dynamo.query(partitionKey, "EVT#")).hasSize(2);
        assertThat(dynamo.get(partitionKey, "META").get("version")).isEqualTo(2L);
        assertThat(
                        Json.longValue(
                                Json.objectValue(dynamo.get(partitionKey, "HEAD"), "purchase"),
                                "amountMinor"))
                .isEqualTo(15000);
        assertThat(
                        dynamo.get(
                                "M#readability-test#IDEMP#"
                                        + Json.sha256("CHANGE_PURCHASE:v1:" + key),
                                "RESULT"))
                .isNull();
        assertThat(dynamo.query(partitionKey, "REF#PAYLOAD#" + Json.sha256(payloadId))).isEmpty();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Merchant-Id", "readability-test")
                .header("Content-Type", "application/json");
    }

    private HttpRequest creation(String key, String body) {
        return request("/v1/sessions")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
