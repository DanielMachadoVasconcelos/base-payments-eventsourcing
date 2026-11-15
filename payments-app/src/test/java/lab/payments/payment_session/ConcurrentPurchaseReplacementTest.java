package lab.payments.payment_session;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static lab.common.Json.sha256;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.springframework.test.web.servlet.client.RestTestClient.*;

import java.time.Duration;
import java.util.UUID;
import java.util.Map;
import java.util.List;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import lab.common.JsonDocumentCodec;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.*;

import lab.common.Dynamo;
import lab.payments.payment_session.application.*;
import lab.payments.payment_session.entry.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

class ConcurrentPurchaseReplacementTest extends PaymentSessionIntegrationTest {
    private static final String MERCHANT = "concurrent-merchant";
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(8);
    @Autowired
    private Dynamo dynamo;
    @Autowired
    private JsonDocumentCodec json;


    @BeforeEach
    void configureMerchant() {
        client = client.mutate().defaultHeader("X-Merchant-Id", MERCHANT).build();
    }

    @Test
    @DisplayName("Should replay the winning replacement When identical concurrent requests prepare the same stream version")
    void shouldReplayTheWinningReplacementWhenIdenticalConcurrentRequestsPrepareTheSameStreamVersion() throws Exception {
        // Given
        var session = createSession();
        String key = newKey();
        var purchase = purchase(15000);
        var scenario = controlConcurrentCommits(session);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = CompletableFuture.supplyAsync(() -> replace(session, key, purchase), executor);
            await().atMost(TEST_TIMEOUT).until(() -> scenarioHasState(scenario, "first-prepared"));

            // When
            var second = CompletableFuture.supplyAsync(() -> replace(session, key, purchase), executor);
            await().atMost(TEST_TIMEOUT).until(() -> scenarioHasState(scenario, "both-prepared"));
            assertThat(first).isNotDone();
            assertThat(second).isNotDone();
            assertStreamVersion(session, 1);

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
        var scenario = controlConcurrentCommits(session);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = CompletableFuture.supplyAsync(() -> replace(session, key, purchase(15000)), executor);
            await().atMost(TEST_TIMEOUT).until(() -> scenarioHasState(scenario, "first-prepared"));

            // When
            var second = CompletableFuture.supplyAsync(() -> replace(session, key, purchase(20000)), executor);
            await().atMost(TEST_TIMEOUT).until(() -> scenarioHasState(scenario, "both-prepared"));
            assertStreamVersion(session, 1);

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
        var scenario = newKey();
        var lookup = post(urlEqualTo("/"))
            .withHeader("X-Amz-Target", equalTo("DynamoDB_20120810.GetItem"))
            .withRequestBody(matchingJsonPath("$.Key.PK.S", equalTo(
                "M#" + MERCHANT + "#IDEMP#" + sha256("CHANGE_PURCHASE:v1:" + key))));
        var delayedLookup = wireMock.stubFor(lookup.inScenario(scenario).whenScenarioStateIs(STARTED)
                                                   .willSetStateTo("lookup-delayed")
                                                   .willReturn(okJson(json.serialize(Map.of())).withFixedDelay(2000)));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var delayed = CompletableFuture.supplyAsync(() -> replace(session, key, purchase(15000)), executor);
            await().atMost(TEST_TIMEOUT).until(() -> scenarioHasState(scenario, "lookup-delayed"));

            // When
            var winner = successfulReplacement(replace(session, key, purchase(15000)));
            assertThat(delayed).isNotDone();

            // Then
            assertThat(successfulReplacement(delayed.get(8, TimeUnit.SECONDS))).isEqualTo(winner);
            assertStreamVersion(session, 2);
        } finally {
            wireMock.removeStub(delayedLookup);
        }
    }

    @Test
    @DisplayName("Should complete the replacement When Spring retries transient DynamoDB transaction conflicts")
    void shouldCompleteTheReplacementWhenSpringRetriesTransientDynamoDBTransactionConflicts() {
        // Given
        var session = createSession();
        var scenario = newKey();
        wireMock.stubFor(transaction(session).inScenario(scenario).whenScenarioStateIs(STARTED)
                                             .willSetStateTo("retry-one")
                                             .willReturn(cancellation("TransactionConflict")));
        wireMock.stubFor(transaction(session).inScenario(scenario).whenScenarioStateIs("retry-one")
                                             .willSetStateTo("retry-two")
                                             .willReturn(cancellation("TransactionConflict")));

        // When
        var result = successfulReplacement(replace(session, newKey(), purchase(15000)));

        // Then
        assertThat(result.purchase().amountMinor()).isEqualTo(15000);
        wireMock.verify(3, transactionRequest(session));
        assertStreamVersion(session, 2);
    }

    @Test
    @DisplayName("Should return a retryable HTTP failure When Spring exhausts transaction conflict retries")
    void shouldReturnARetryableHTTPFailureWhenSpringExhaustsTransactionConflictRetries() {
        // Given
        var session = createSession();
        String key = newKey();
        var failure = wireMock.stubFor(transaction(session).willReturn(cancellation("TransactionConflict")));
        try {
            // When
            replace(session, key, purchase(15000)).expectStatus().isEqualTo(503);

            // Then
            wireMock.verify(4, transactionRequest(session));
            assertStreamVersion(session, 1);
        } finally {
            wireMock.removeStub(failure);
        }
        successfulReplacement(replace(session, key, purchase(15000)));
        assertStreamVersion(session, 2);
    }

    @Test
    @DisplayName("Should avoid retrying the transaction When cancellation reports a failed condition")
    void shouldAvoidRetryingTheTransactionWhenCancellationReportsAFailedCondition() {
        // Given
        var session = createSession();
        var failure = wireMock.stubFor(transaction(session).willReturn(cancellation("ConditionalCheckFailed")));
        try {
            // When
            replace(session, newKey(), purchase(15000)).expectStatus().isEqualTo(503);

            // Then
            wireMock.verify(1, transactionRequest(session));
            assertStreamVersion(session, 1);
        } finally {
            wireMock.removeStub(failure);
        }
    }

    @Test
    @DisplayName("Should replay the completed replacement When the commit response is lost")
    void shouldReplayTheCompletedReplacementWhenTheCommitResponseIsLost() {
        // Given
        var session = createSession();
        String key = newKey();
        // WireMock forwards the commit before delaying its response beyond the SDK socket timeout.
        var lostResponse = wireMock.stubFor(transaction(session)
                                                .willReturn(aResponse().proxiedFrom("http://localhost:8000")
                                                                       .withFixedDelay(6000)));
        try {
            // When
            replace(session, key, purchase(15000)).expectStatus().isEqualTo(503);

            // Then
            assertStreamVersion(session, 2);
        } finally {
            wireMock.removeStub(lostResponse);
        }
        var recovered = successfulReplacement(replace(session, key, purchase(15000)));
        assertThat(read(session)).isEqualTo(recovered);
        assertStreamVersion(session, 2);
    }

    private PaymentSessionResponse createSession() {
        return client.post().uri("/v1/sessions")
                     .header("Idempotency-Key", newKey())
                     .contentType(MediaType.APPLICATION_JSON)
                     .body(new CreatePaymentSessionRequest(10000, "BRL", null, null))
                     .exchange()
                     .expectStatus()
                     .isCreated()
                     .expectBody(PaymentSessionResponse.class)
                     .returnResult()
                     .getResponseBody();
    }

    private ResponseSpec replace(PaymentSessionResponse session,
                                 String key,
                                 ChangePurchaseRequest purchase) {
        return client.put().uri("/v1/sessions/{id}", session.sessionId())
                     .header("Idempotency-Key", key)
                     .header(HttpHeaders.IF_MATCH, "\"1\"")
                     .contentType(MediaType.APPLICATION_JSON)
                     .body(purchase)
                     .exchange();
    }

    private PaymentSessionResponse successfulReplacement(ResponseSpec response) {
        return response.expectStatus()
                       .isOk()
                       .expectHeader()
                       .valueEquals(HttpHeaders.ETAG, "\"2\"")
                       .expectBody(PaymentSessionResponse.class)
                       .returnResult()
                       .getResponseBody();
    }

    private void assertProblem(ResponseSpec response,
                               String code) {

        var problem = response.expectStatus()
                              .isEqualTo(409)
                              .expectBody(ProblemDetail.class)
                              .returnResult()
                              .getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getProperties()).containsEntry("code", code);
    }

    private PaymentSessionResponse read(PaymentSessionResponse session) {
        return client.get().uri("/v1/sessions/{id}", session.sessionId())
                     .exchange()
                     .expectStatus()
                     .isOk()
                     .expectBody(PaymentSessionResponse.class)
                     .returnResult()
                     .getResponseBody();
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

    private ResponseDefinitionBuilder cancellation(String reason) {
        return aResponse().withStatus(400).withHeader("Content-Type", "application/x-amz-json-1.0")
                          .withBody(json.serialize(
                              Map.of("__type", "com.amazonaws.dynamodb.v20120810#TransactionCanceledException",
                                     "CancellationReasons",
                                     List.of(Map.of("Code", reason, "Message", "Injected transaction cancellation")))));
    }

    private MappingBuilder transaction(PaymentSessionResponse session) {
        return post(urlEqualTo("/")).atPriority(1)
                                    .withHeader("X-Amz-Target", equalTo("DynamoDB_20120810.TransactWriteItems"))
                                    .withRequestBody(matchingJsonPath("$.TransactItems[0].Put.Item.PK.S",
                                                                      equalTo(partition(session))));
    }

    private RequestPatternBuilder transactionRequest(PaymentSessionResponse session) {
        return postRequestedFor(urlEqualTo("/"))
            .withHeader("X-Amz-Target", equalTo("DynamoDB_20120810.TransactWriteItems"))
            .withRequestBody(matchingJsonPath("$.TransactItems[0].Put.Item.PK.S", equalTo(partition(session))))
            .withRequestBody(matchingJsonPath("$.TransactItems[0].Put.Item.SK.S", equalTo("EVT#00000000000000000002")));
    }

    private String partition(PaymentSessionResponse session) {
        return "M#" + MERCHANT + "#S#" + session.sessionId();
    }

    private boolean scenarioHasState(String scenario,
                                     String state) {
        return wireMock.getAllScenarios().getScenarios().stream()
                       .anyMatch(value -> value.getName().equals(scenario) && value.getState().equals(state));
    }

    private String controlConcurrentCommits(PaymentSessionResponse session) {
        var scenario = newKey();
        wireMock.stubFor(transaction(session).inScenario(scenario).whenScenarioStateIs(STARTED)
                                             .willSetStateTo("first-prepared")
                                             .willReturn(cancellation("TransactionConflict").withFixedDelay(2000)));
        wireMock.stubFor(transaction(session).inScenario(scenario).whenScenarioStateIs("first-prepared")
                                             .willSetStateTo("both-prepared")
                                             .willReturn(cancellation("TransactionConflict").withFixedDelay(3500)));
        return scenario;
    }

}
