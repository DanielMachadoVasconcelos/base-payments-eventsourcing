package lab.payments.payment_session;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import lab.common.Dynamo;
import lab.payments.payment_session.application.*;
import lab.payments.payment_session.entry.*;
import org.junit.jupiter.api.*;
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
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

@Tag("compose")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrentPurchaseReplacementTest {
    private static final String TABLE = "payments_concurrent_" + UUID.randomUUID().toString().replace("-", "");
    private static final String MERCHANT = "concurrent-merchant";
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(8);
    @MockitoSpyBean
    private Dynamo dynamo;
    @MockitoSpyBean
    private PaymentSessionEventStore eventStore;

    @Value("${local.server.port}")
    private int port;

    private RestTestClient client;

    @BeforeEach
    void configureClient() {
        var requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(12));
        client = RestTestClient.bindToServer(requestFactory).baseUrl("http://localhost:" + port)
                               .defaultHeader("X-Merchant-Id", MERCHANT).build();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("lab.table", () -> TABLE);
    }

    @AfterAll
    static void cleanup(@org.springframework.beans.factory.annotation.Autowired Dynamo dynamo) {
        dynamo.client.deleteTable(request -> request.tableName(TABLE));
    }

    @Test
    @DisplayName("Should replay the winning replacement When identical concurrent requests prepare the same stream version")
    void shouldReplayTheWinningReplacementWhenIdenticalConcurrentRequestsPrepareTheSameStreamVersion() throws Exception {
        // Given
        var session = createSession();
        String key = newKey();
        var purchase = purchase(15000);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor(); var commits = controlConcurrentCommits()) {
            var first = CompletableFuture.supplyAsync(() -> replace(session, key, purchase), executor);
            await().atMost(TEST_TIMEOUT).until(commits.firstPrepared()::isDone);

            // When
            var second = CompletableFuture.supplyAsync(() -> replace(session, key, purchase), executor);
            await().atMost(TEST_TIMEOUT).until(commits.secondPrepared()::isDone);
            assertThat(first).isNotDone();
            assertThat(second).isNotDone();
            assertStreamVersion(session, 1);
            commits.resume();

            // Then
            var winner = successfulReplacement(first.get(8, TimeUnit.SECONDS));
            assertThat(successfulReplacement(second.get(8, TimeUnit.SECONDS))).isEqualTo(winner);
            assertThat(successfulReplacement(replace(session, key, purchase))).isEqualTo(winner);
            assertThat(winner.purchase().amountMinor()).isEqualTo(15000);
            assertStreamVersion(session, 2);
        }
    }

    @Test
    @DisplayName("Should reject conflicting content When concurrent replacements share an idempotency key")
    void shouldRejectConflictingContentWhenConcurrentReplacementsShareAnIdempotencyKey() throws Exception {
        // Given
        var session = createSession();
        String key = newKey();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor(); var commits = controlConcurrentCommits()) {
            var first = CompletableFuture.supplyAsync(() -> replace(session, key, purchase(15000)), executor);
            await().atMost(TEST_TIMEOUT).until(commits.firstPrepared()::isDone);

            // When
            var second = CompletableFuture.supplyAsync(() -> replace(session, key, purchase(20000)), executor);
            await().atMost(TEST_TIMEOUT).until(commits.secondPrepared()::isDone);
            assertStreamVersion(session, 1);
            commits.resume();

            // Then
            var winner = successfulReplacement(first.get(8, TimeUnit.SECONDS));
            assertProblem(second.get(8, TimeUnit.SECONDS), "IDEMPOTENCY_KEY_CONFLICT");
            assertProblem(replace(session, key, purchase(20000)), "IDEMPOTENCY_KEY_CONFLICT");
            assertThat(read(session)).isEqualTo(winner);
            assertStreamVersion(session, 2);
        }
    }

    @Test
    @DisplayName("Should replay the winning result When a concurrent commit follows the initial idempotency lookup")
    void shouldReplayTheWinningResultWhenAConcurrentCommitFollowsTheInitialIdempotencyLookup() throws Exception {
        // Given
        var session = createSession();
        String key = newKey();
        var loaded = new CompletableFuture<Void>();
        var resume = new CompletableFuture<Void>();
        var first = new AtomicInteger();
        doAnswer(invocation -> {
            if (first.getAndIncrement() == 0) {
                loaded.complete(null);
                resume.get(12, TimeUnit.SECONDS);
            }
            return invocation.callRealMethod();
        }).when(eventStore).load(any());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var delayed = CompletableFuture.supplyAsync(() -> replace(session, key, purchase(15000)), executor);
                await().atMost(TEST_TIMEOUT).until(loaded::isDone);

                // When
                var winner = successfulReplacement(replace(session, key, purchase(15000)));
                resume.complete(null);

                // Then
                assertThat(successfulReplacement(delayed.get(8, TimeUnit.SECONDS))).isEqualTo(winner);
                assertStreamVersion(session, 2);
            } finally {
                resume.complete(null);
            }
        }
    }

    @Test
    @DisplayName("Should complete the replacement When Spring retries transient DynamoDB transaction conflicts")
    void shouldCompleteTheReplacementWhenSpringRetriesTransientDynamoDBTransactionConflicts() {
        // Given
        var session = createSession();
        clearInvocations(dynamo);
        var attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() < 2) throw cancellation("TransactionConflict");
            return invocation.callRealMethod();
        }).when(dynamo).transaction(anyList(), anyString());

        // When
        var result = successfulReplacement(replace(session, newKey(), purchase(15000)));

        // Then
        assertThat(result.purchase().amountMinor()).isEqualTo(15000);
        verify(dynamo, times(3)).transaction(anyList(), anyString());
        assertStreamVersion(session, 2);
    }

    @Test
    @DisplayName("Should return a retryable HTTP failure When Spring exhausts transaction conflict retries")
    void shouldReturnARetryableHTTPFailureWhenSpringExhaustsTransactionConflictRetries() {
        // Given
        var session = createSession();
        clearInvocations(dynamo);
        String key = newKey();
        doThrow(cancellation("TransactionConflict")).when(dynamo).transaction(anyList(), anyString());

        // When
        replace(session, key, purchase(15000)).expectStatus().isEqualTo(503);

        // Then
        verify(dynamo, times(4)).transaction(anyList(), anyString());
        assertStreamVersion(session, 1);
        doCallRealMethod().when(dynamo).transaction(anyList(), anyString());
        successfulReplacement(replace(session, key, purchase(15000)));
        assertStreamVersion(session, 2);
    }

    @Test
    @DisplayName("Should avoid retrying the transaction When cancellation reports a failed condition")
    void shouldAvoidRetryingTheTransactionWhenCancellationReportsAFailedCondition() {
        // Given
        var session = createSession();
        clearInvocations(dynamo);
        doThrow(cancellation("ConditionalCheckFailed")).when(dynamo).transaction(anyList(), anyString());

        // When
        replace(session, newKey(), purchase(15000)).expectStatus().isEqualTo(503);

        // Then
        verify(dynamo, times(1)).transaction(anyList(), anyString());
        assertStreamVersion(session, 1);
    }

    @Test
    @DisplayName("Should replay the completed replacement When the commit response is lost")
    void shouldReplayTheCompletedReplacementWhenTheCommitResponseIsLost() {
        // Given
        var session = createSession();
        String key = newKey();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw SdkClientException.create("Simulated lost commit response");
        }).when(dynamo).transaction(anyList(), anyString());

        // When
        replace(session, key, purchase(15000)).expectStatus().isEqualTo(503);

        // Then
        assertStreamVersion(session, 2);
        doCallRealMethod().when(dynamo).transaction(anyList(), anyString());
        var recovered = successfulReplacement(replace(session, key, purchase(15000)));
        assertThat(read(session)).isEqualTo(recovered);
        assertStreamVersion(session, 2);
    }

    private PaymentSessionResponse createSession() {
        return client.post().uri("/v1/sessions").header("Idempotency-Key", newKey())
                     .contentType(MediaType.APPLICATION_JSON)
                     .body(new CreatePaymentSessionRequest(10000, "BRL", null, null)).exchange().expectStatus()
                     .isCreated().expectBody(PaymentSessionResponse.class).returnResult().getResponseBody();
    }

    private RestTestClient.ResponseSpec replace(PaymentSessionResponse session,
                                                String key,
                                                ChangePurchaseRequest purchase) {
        return client.put().uri("/v1/sessions/{id}", session.sessionId()).header("Idempotency-Key", key)
                     .header(HttpHeaders.IF_MATCH, "\"1\"").contentType(MediaType.APPLICATION_JSON).body(purchase)
                     .exchange();
    }

    private PaymentSessionResponse successfulReplacement(RestTestClient.ResponseSpec response) {
        return response.expectStatus().isOk().expectHeader().valueEquals(HttpHeaders.ETAG, "\"2\"")
                       .expectBody(PaymentSessionResponse.class).returnResult().getResponseBody();
    }

    private void assertProblem(RestTestClient.ResponseSpec response,
                               String code) {
        var problem = response.expectStatus().isEqualTo(409).expectBody(ProblemDetail.class).returnResult()
                              .getResponseBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getProperties()).containsEntry("code", code);
    }

    private PaymentSessionResponse read(PaymentSessionResponse session) {
        return client.get().uri("/v1/sessions/{id}", session.sessionId()).exchange().expectStatus().isOk()
                     .expectBody(PaymentSessionResponse.class).returnResult().getResponseBody();
    }

    private void assertStreamVersion(PaymentSessionResponse session,
                                     long version) {
        String partition = "M#" + MERCHANT + "#S#" + session.sessionId();
        assertThat(dynamo.query(partition, "EVT#")).hasSize((int) version);
        assertThat(dynamo.get(partition, "META").get("version")).isEqualTo(version);
        assertThat(dynamo.get(partition, "HEAD").get("version")).isEqualTo(version);
    }

    private ChangePurchaseRequest purchase(long amount) {
        return new ChangePurchaseRequest(amount, "BRL", null, null);
    }

    private String newKey() {
        return UUID.randomUUID().toString();
    }

    private TransactionCanceledException cancellation(String reason) {
        return TransactionCanceledException.builder()
                                           .cancellationReasons(CancellationReason.builder().code(reason).build())
                                           .build();
    }

    private ConcurrentCommits controlConcurrentCommits() {
        var control = new ConcurrentCommits();
        var attempts = new AtomicInteger();
        doAnswer(invocation -> {
            int attempt = attempts.getAndIncrement();
            if (attempt == 0) {
                control.firstPrepared().complete(null);
                control.resumeFirst().get(12, TimeUnit.SECONDS);
                try {
                    return invocation.callRealMethod();
                } finally {
                    control.firstCommitted().complete(null);
                }
            }
            if (attempt == 1) {
                control.secondPrepared().complete(null);
                control.firstCommitted().get(12, TimeUnit.SECONDS);
            }
            return invocation.callRealMethod();
        }).when(dynamo).transaction(anyList(), anyString());
        return control;
    }

    private record ConcurrentCommits(CompletableFuture<Void> firstPrepared, CompletableFuture<Void> secondPrepared,
                                     CompletableFuture<Void> resumeFirst,
                                     CompletableFuture<Void> firstCommitted) implements AutoCloseable {
        ConcurrentCommits() {
            this(new CompletableFuture<>(), new CompletableFuture<>(), new CompletableFuture<>(),
                 new CompletableFuture<>());
        }

        void resume() {
            resumeFirst.complete(null);
        }

        @Override
        public void close() {
            resume();
            firstCommitted.complete(null);
        }
    }
}
