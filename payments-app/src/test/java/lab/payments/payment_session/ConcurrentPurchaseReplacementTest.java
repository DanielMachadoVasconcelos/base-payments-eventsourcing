package lab.payments.payment_session;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lab.common.Dynamo;
import lab.payments.payment_session.application.*;
import lab.payments.payment_session.domain.*;
import lab.payments.payment_session.entry.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import software.amazon.awssdk.core.exception.SdkClientException;

@Tag("compose")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"lab.synchronous-wait-ms=3000", "lab.command-poll-ms=20"})
class ConcurrentPurchaseReplacementTest {
    private static final String TABLE =
            "payments_concurrent_" + UUID.randomUUID().toString().replace("-", "");
    private static final String MERCHANT = "concurrent-merchant";
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(8);
    @Autowired private Dynamo dynamo;
    @MockitoSpyBean private PaymentSessionEventStore eventStore;
    @MockitoSpyBean private SessionCommandReservations reservations;

    @Value("${local.server.port}")
    private int port;

    private RestTestClient client;

    @BeforeEach
    void configureClient() {
        // Observe the first HTTP response instead of transport-level retries of 503.
        var requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(12));
        client =
                RestTestClient.bindToServer(requestFactory)
                        .baseUrl("http://localhost:" + port)
                        .defaultHeader("X-Merchant-Id", MERCHANT)
                        .build();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("lab.table", () -> TABLE);
    }

    @AfterAll
    static void cleanup(@Autowired Dynamo dynamo) {
        dynamo.client.deleteTable(request -> request.tableName(TABLE));
    }

    @Test
    @DisplayName(
            "Should return the original replacement result When an identical concurrent request waits for completion")
    void
            shouldReturnTheOriginalReplacementResultWhenAnIdenticalConcurrentRequestWaitsForCompletion()
                    throws Exception {
        // Given
        var session = createSession();
        String key = UUID.randomUUID().toString();
        var purchase =
                new ChangePurchaseRequest(15000, "BRL", "replacement", Map.of("revision", 2));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
                var processing = pauseFirstCommit()) {
            var first =
                    CompletableFuture.supplyAsync(() -> replace(session, key, purchase), executor);
            await().atMost(TEST_TIMEOUT).until(processing.firstCommitEntered()::isDone);

            // When
            var second =
                    CompletableFuture.supplyAsync(() -> replace(session, key, purchase), executor);
            await().atMost(TEST_TIMEOUT).until(processing.secondRequestWaiting()::isDone);
            assertThat(first).isNotDone();
            assertThat(second).isNotDone();
            assertStreamVersion(session, 1);
            processing.resume();

            // Then
            var winner = successfulReplacement(first.get(8, TimeUnit.SECONDS));
            var replay = successfulReplacement(second.get(8, TimeUnit.SECONDS));
            assertThat(winner.version()).isEqualTo(2);
            assertThat(winner.purchase())
                    .isEqualTo(
                            new PurchaseResponse(
                                    15000, "BRL", "replacement", Map.of("revision", 2)));
            assertThat(replay).isEqualTo(winner);
            assertThat(successfulReplacement(replace(session, key, purchase))).isEqualTo(winner);
            assertThat(eventStore.previouslyCompletedResponse(idempotency(key, purchase)))
                    .hasValueSatisfying(
                            result ->
                                    assertThat(result.sessionId()).isEqualTo(session.sessionId()));
            assertStreamVersion(session, 2);
            assertThat(dynamo.get(commandPartition(key), "PROCESSING")).isNull();
        }
    }

    @Test
    @DisplayName(
            "Should reject conflicting content When a concurrent request waits for the same idempotency key")
    void shouldRejectConflictingContentWhenAConcurrentRequestWaitsForTheSameIdempotencyKey()
            throws Exception {
        // Given
        var session = createSession();
        String key = UUID.randomUUID().toString();
        var purchase = new ChangePurchaseRequest(15000, "BRL", null, null);
        var conflicting = new ChangePurchaseRequest(20000, "BRL", null, null);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
                var processing = pauseFirstCommit()) {
            var first =
                    CompletableFuture.supplyAsync(() -> replace(session, key, purchase), executor);
            await().atMost(TEST_TIMEOUT).until(processing.firstCommitEntered()::isDone);

            // When
            var second =
                    CompletableFuture.supplyAsync(
                            () -> replace(session, key, conflicting), executor);
            await().atMost(TEST_TIMEOUT).until(processing.secondRequestWaiting()::isDone);
            assertThat(first).isNotDone();
            assertThat(second).isNotDone();
            assertStreamVersion(session, 1);
            processing.resume();

            // Then
            var winner = successfulReplacement(first.get(8, TimeUnit.SECONDS));
            var problem =
                    second.get(8, TimeUnit.SECONDS)
                            .expectStatus()
                            .isEqualTo(409)
                            .expectHeader()
                            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                            .expectBody(ProblemDetail.class)
                            .returnResult()
                            .getResponseBody();
            assertThat(problem).isNotNull();
            assertThat(problem.getProperties()).containsEntry("code", "IDEMPOTENCY_KEY_CONFLICT");
            replace(session, key, conflicting).expectStatus().isEqualTo(409);
            var current =
                    client.get()
                            .uri("/v1/sessions/{id}", session.sessionId())
                            .exchange()
                            .expectStatus()
                            .isOk()
                            .expectBody(PaymentSessionResponse.class)
                            .returnResult()
                            .getResponseBody();
            assertThat(current).isEqualTo(winner);
            assertThat(eventStore.previouslyCompletedResponse(idempotency(key, purchase)))
                    .isPresent();
            assertStreamVersion(session, 2);
            assertThat(dynamo.get(commandPartition(key), "PROCESSING")).isNull();
        }
    }

    @Test
    @DisplayName(
            "Should preserve the active owner When a concurrent request exceeds its wait deadline")
    void shouldPreserveTheActiveOwnerWhenAConcurrentRequestExceedsItsWaitDeadline()
            throws Exception {
        // Given
        var session = createSession();
        String key = UUID.randomUUID().toString();
        var purchase = new ChangePurchaseRequest(15000, "BRL", null, null);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
                var processing = pauseFirstCommit()) {
            var first =
                    CompletableFuture.supplyAsync(() -> replace(session, key, purchase), executor);
            await().atMost(TEST_TIMEOUT).until(processing.firstCommitEntered()::isDone);

            // When
            var second =
                    CompletableFuture.supplyAsync(() -> replace(session, key, purchase), executor);
            await().atMost(TEST_TIMEOUT).until(processing.secondRequestWaiting()::isDone);
            var problem =
                    second.get(8, TimeUnit.SECONDS)
                            .expectStatus()
                            .isEqualTo(503)
                            .expectBody(ProblemDetail.class)
                            .returnResult()
                            .getResponseBody();

            // Then
            assertThat(problem).isNotNull();
            assertThat(problem.getProperties())
                    .containsEntry("code", "SESSION_COMMAND_IN_PROGRESS");
            assertThat(first).isNotDone();
            assertStreamVersion(session, 1);
            assertThat(dynamo.get(commandPartition(key), "PROCESSING")).isNotNull();
            assertThat(eventStore.previouslyCompletedResponse(idempotency(key, purchase)))
                    .isEmpty();
            processing.resume();
            var winner = successfulReplacement(first.get(8, TimeUnit.SECONDS));
            assertThat(successfulReplacement(replace(session, key, purchase))).isEqualTo(winner);
            assertStreamVersion(session, 2);
            assertThat(dynamo.get(commandPartition(key), "PROCESSING")).isNull();
        }
    }

    @Test
    @DisplayName("Should replay the completed replacement When the commit response is lost")
    void shouldReplayTheCompletedReplacementWhenTheCommitResponseIsLost() {
        // Given
        var session = createSession();
        String key = UUID.randomUUID().toString();
        var purchase = new ChangePurchaseRequest(15000, "BRL", null, null);
        var first = new AtomicBoolean(true);
        doAnswer(
                        invocation -> {
                            Object result = invocation.callRealMethod();
                            if (first.compareAndSet(true, false)) {
                                throw SdkClientException.create("Simulated lost commit response");
                            }
                            return result;
                        })
                .when(eventStore)
                .append(any(), any());

        // When
        replace(session, key, purchase).expectStatus().isEqualTo(503);

        // Then
        assertStreamVersion(session, 2);
        assertThat(dynamo.get(commandPartition(key), "PROCESSING")).isNull();
        var recovered = successfulReplacement(replace(session, key, purchase));
        assertThat(recovered.purchase().amountMinor()).isEqualTo(15000);
        assertThat(successfulReplacement(replace(session, key, purchase))).isEqualTo(recovered);
        assertStreamVersion(session, 2);
    }

    @Test
    @DisplayName(
            "Should retain ownership until retry takeover When persistence fails before completion")
    void shouldRetainOwnershipUntilRetryTakeoverWhenPersistenceFailsBeforeCompletion() {
        // Given
        var session = createSession();
        String key = UUID.randomUUID().toString();
        var purchase = new ChangePurchaseRequest(15000, "BRL", null, null);
        var first = new AtomicBoolean(true);
        doAnswer(
                        invocation -> {
                            if (first.compareAndSet(true, false)) {
                                throw SdkClientException.create(
                                        "Simulated unavailable persistence");
                            }
                            return invocation.callRealMethod();
                        })
                .when(eventStore)
                .append(any(), any());

        // When
        replace(session, key, purchase).expectStatus().isEqualTo(503);

        // Then
        assertStreamVersion(session, 1);
        assertThat(dynamo.get(commandPartition(key), "PROCESSING")).isNotNull();
        assertThat(eventStore.previouslyCompletedResponse(idempotency(key, purchase))).isEmpty();
        dynamo.client.updateItem(
                builder ->
                        builder.tableName(TABLE)
                                .key(Dynamo.key(commandPartition(key), "PROCESSING"))
                                .updateExpression("SET leaseUntil = :expired")
                                .expressionAttributeValues(
                                        Map.of(":expired", Dynamo.numberAttribute(0))));
        var recovered = successfulReplacement(replace(session, key, purchase));
        assertThat(recovered.purchase().amountMinor()).isEqualTo(15000);
        assertStreamVersion(session, 2);
        assertThat(dynamo.get(commandPartition(key), "PROCESSING")).isNull();
    }

    private PaymentSessionResponse createSession() {
        return client.post()
                .uri("/v1/sessions")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreatePaymentSessionRequest(10000, "BRL", null, null))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(PaymentSessionResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private RestTestClient.ResponseSpec replace(
            PaymentSessionResponse session, String key, ChangePurchaseRequest purchase) {
        return client.put()
                .uri("/v1/sessions/{id}", session.sessionId())
                .header("Idempotency-Key", key)
                .header(HttpHeaders.IF_MATCH, "\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(purchase)
                .exchange();
    }

    private PaymentSessionResponse successfulReplacement(RestTestClient.ResponseSpec response) {
        return response.expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(HttpHeaders.ETAG, "\"2\"")
                .expectBody(PaymentSessionResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private void assertStreamVersion(PaymentSessionResponse session, long version) {
        String partition = "M#" + MERCHANT + "#S#" + session.sessionId();
        assertThat(dynamo.query(partition, "EVT#")).hasSize((int) version);
        assertThat(dynamo.get(partition, "META").get("version")).isEqualTo(version);
        assertThat(dynamo.get(partition, "HEAD").get("version")).isEqualTo(version);
    }

    private String commandPartition(String key) {
        return "M#" + MERCHANT + "#IDEMP#" + lab.common.Json.sha256("CHANGE_PURCHASE:v1:" + key);
    }

    private IdempotencyRequest idempotency(String key, ChangePurchaseRequest request) {
        var purchase =
                new Purchase(
                        ((Number) request.amountMinor()).longValue(),
                        request.currency(),
                        request.merchantReference() == null ? "" : request.merchantReference(),
                        request.attributes() == null ? Map.of() : request.attributes());
        return new IdempotencyRequest(
                new IdempotencyScope(new MerchantId(MERCHANT), "CHANGE_PURCHASE", "v1"),
                key,
                lab.common.Json.fingerprint(Map.of("purchase", purchase, "expectedVersion", 1L)));
    }

    private ProcessingControl pauseFirstCommit() {
        var control = new ProcessingControl();
        var first = new AtomicBoolean(true);
        doAnswer(
                        invocation -> {
                            if (first.compareAndSet(true, false)) {
                                control.firstCommitEntered().complete(null);
                                control.resumeCommit().get(12, TimeUnit.SECONDS);
                            }
                            return invocation.callRealMethod();
                        })
                .when(eventStore)
                .append(any(), any());
        doAnswer(
                        invocation -> {
                            Object result = invocation.callRealMethod();
                            if (((Optional<?>) result).isEmpty()) {
                                control.secondRequestWaiting().complete(null);
                            }
                            return result;
                        })
                .when(reservations)
                .acquire(any());
        return control;
    }

    private record ProcessingControl(
            CompletableFuture<Void> firstCommitEntered,
            CompletableFuture<Void> secondRequestWaiting,
            CompletableFuture<Void> resumeCommit)
            implements AutoCloseable {
        ProcessingControl() {
            this(new CompletableFuture<>(), new CompletableFuture<>(), new CompletableFuture<>());
        }

        void resume() {
            resumeCommit.complete(null);
        }

        @Override
        public void close() {
            resume();
        }
    }
}
