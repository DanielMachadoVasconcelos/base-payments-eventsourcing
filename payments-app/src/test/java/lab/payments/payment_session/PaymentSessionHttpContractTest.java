package lab.payments.payment_session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lab.common.Dynamo;
import lab.payments.payment_session.entry.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.context.WebApplicationContext;

@Tag("compose")
@SpringBootTest
class PaymentSessionHttpContractTest {
    private static final String TABLE =
            "payments_http_" + UUID.randomUUID().toString().replace("-", "");
    private static final String MERCHANT = "contract-merchant";
    private RestTestClient client;

    @BeforeEach
    void configureClient(@Autowired WebApplicationContext context) {
        client = RestTestClient.bindToApplicationContext(context).build();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("lab.table", () -> TABLE);
        properties.add("lab.workers-enabled", () -> false);
    }

    @AfterAll
    static void cleanup(@Autowired Dynamo dynamo) {
        dynamo.client.deleteTable(request -> request.tableName(TABLE));
    }

    @Test
    @DisplayName(
            "Should create a session with default optional fields When only required purchase fields are provided")
    void shouldCreateSessionWithDefaultsWhenOnlyRequiredFieldsAreProvided() {
        // Given
        var purchase = new CreatePaymentSessionRequest(10000, "BRL", null, null);

        // When
        var result = create(MERCHANT, newKey(), purchase);

        // Then
        var response =
                result.expectStatus()
                        .isCreated()
                        .expectHeader()
                        .valueEquals(HttpHeaders.ETAG, "\"1\"")
                        .expectHeader()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                        .expectBody(PaymentSessionResponse.class)
                        .returnResult()
                        .getResponseBody();
        assertThat(response).isNotNull();
        assertThat(response.sessionId()).isNotBlank();
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.state()).isEqualTo("OPEN");
        assertThat(response.expiresAt()).isNotNull();
        assertThat(response.purchase()).isEqualTo(new PurchaseResponse(10000, "BRL", "", Map.of()));
    }

    @Test
    @DisplayName(
            "Should replace the entire purchase and reset omitted fields When the current version is supplied")
    void shouldReplaceEntirePurchaseWhenCurrentVersionIsSupplied() {
        // Given
        var original =
                createSession(
                        MERCHANT,
                        newKey(),
                        new CreatePaymentSessionRequest(
                                10000, "BRL", "old-cart", Map.of("delivery", "reception")));

        // When
        var result =
                replace(MERCHANT, original.sessionId(), newKey())
                        .header(HttpHeaders.IF_MATCH, "\"1\"")
                        .body(new ChangePurchaseRequest(15000, "USD", null, null))
                        .exchange();

        // Then
        var changed =
                result.expectStatus()
                        .isOk()
                        .expectHeader()
                        .valueEquals(HttpHeaders.ETAG, "\"2\"")
                        .expectBody(PaymentSessionResponse.class)
                        .returnResult()
                        .getResponseBody();
        assertThat(changed).isNotNull();
        assertThat(changed.sessionId()).isEqualTo(original.sessionId());
        assertThat(changed.version()).isEqualTo(2);
        assertThat(changed.purchase()).isEqualTo(new PurchaseResponse(15000, "USD", "", Map.of()));
        assertThat(read(MERCHANT, original.sessionId())).isEqualTo(changed);
    }

    @ParameterizedTest(name = "missing {0}")
    @ValueSource(strings = {"X-Merchant-Id", "Idempotency-Key", "If-Match"})
    @DisplayName("Should reject the request When a required header is missing")
    void shouldRejectRequestWhenRequiredHeaderIsMissing(String missingHeader) {
        // Given
        var request =
                client.put()
                        .uri("/v1/sessions/unknown")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new ChangePurchaseRequest(15000, "BRL", null, null));
        var headers = new HttpHeaders();
        headers.set("X-Merchant-Id", MERCHANT);
        headers.set("Idempotency-Key", newKey());
        headers.set(HttpHeaders.IF_MATCH, "\"1\"");
        headers.remove(missingHeader);
        request.headers(values -> values.addAll(headers));

        // When
        var response = request.exchange();

        // Then
        assertProblem(response, 400, "INVALID_REQUEST");
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    @DisplayName("Should reject the purchase When the amount is missing or invalid")
    void shouldRejectPurchaseWhenAmountIsInvalid(Object amount) {
        // Given
        var purchase = new CreatePaymentSessionRequest(amount, "BRL", null, null);

        // When
        var response = create(MERCHANT, newKey(), purchase);

        // Then
        assertProblem(response, 400, "INVALID_AMOUNT");
    }

    static Stream<Object> invalidAmounts() {
        return Stream.of(null, 0, -1, 1.5, "not-a-number", "9223372036854775808");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "brl", "ZZZ"})
    @DisplayName("Should reject the purchase When the currency is missing or invalid")
    void shouldRejectPurchaseWhenCurrencyIsInvalid(String currency) {
        // Given
        var purchase = new CreatePaymentSessionRequest(10000, currency, null, null);

        // When
        var response = create(MERCHANT, newKey(), purchase);

        // Then
        assertProblem(response, 400, "INVALID_CURRENCY");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "invalid merchant",
                "invalid!",
                "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyzabcdefghijklm"
            })
    @DisplayName("Should reject the request When the merchant identifier is invalid")
    void shouldRejectRequestWhenMerchantIsInvalid(String merchant) {
        // Given
        var purchase = purchase();

        // When
        var response = create(merchant, newKey(), purchase);

        // Then
        assertProblem(response, 400, "INVALID_MERCHANT");
    }

    @ParameterizedTest
    @MethodSource("invalidKeys")
    @DisplayName("Should reject the request When the idempotency key is invalid")
    void shouldRejectRequestWhenIdempotencyKeyIsInvalid(String key) {
        // Given
        var purchase = purchase();

        // When
        var response = create(MERCHANT, key, purchase);

        // Then
        assertProblem(response, 400, "INVALID_IDEMPOTENCY_KEY");
    }

    static Stream<String> invalidKeys() {
        return Stream.of("", " ", "k".repeat(257));
    }

    @Test
    @DisplayName("Should reject malformed JSON and allow correction When the same key is retried")
    void shouldAllowCorrectionWhenMalformedJsonIsRetriedWithTheSameKey() {
        // Given
        String key = newKey();

        // When
        var invalid =
                client.post()
                        .uri("/v1/sessions")
                        .header("X-Merchant-Id", MERCHANT)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{")
                        .exchange();

        // Then
        assertProblem(invalid, 400, "INVALID_REQUEST");
        create(MERCHANT, key, purchase()).expectStatus().isCreated();
    }

    @Test
    @DisplayName("Should return session not found When reading an unknown session")
    void shouldReturnNotFoundWhenReadingUnknownSession() {
        // Given
        String sessionId = newKey();

        // When
        var response =
                client.get()
                        .uri("/v1/sessions/{id}", sessionId)
                        .header("X-Merchant-Id", MERCHANT)
                        .exchange();

        // Then
        assertProblem(response, 404, "PAYMENT_SESSION_NOT_FOUND");
    }

    @Test
    @DisplayName("Should return session not found When replacing an unknown session")
    void shouldReturnNotFoundWhenReplacingUnknownSession() {
        // Given
        String sessionId = newKey();

        // When
        var response =
                replace(MERCHANT, sessionId, newKey())
                        .header(HttpHeaders.IF_MATCH, "\"1\"")
                        .body(new ChangePurchaseRequest(15000, "BRL", null, null))
                        .exchange();

        // Then
        assertProblem(response, 404, "PAYMENT_SESSION_NOT_FOUND");
    }

    @Test
    @DisplayName(
            "Should preserve the owner's purchase When another merchant attempts to replace it")
    void shouldPreserveOwnersPurchaseWhenAnotherMerchantAttemptsReplacement() {
        // Given
        var original = createSession(MERCHANT, newKey(), purchase());

        // When
        var response =
                replace("other-merchant", original.sessionId(), newKey())
                        .header(HttpHeaders.IF_MATCH, "\"1\"")
                        .body(new ChangePurchaseRequest(15000, "BRL", null, null))
                        .exchange();

        // Then
        assertProblem(response, 404, "PAYMENT_SESSION_NOT_FOUND");
        assertThat(read(MERCHANT, original.sessionId())).isEqualTo(original);
    }

    @Test
    @DisplayName(
            "Should create independent sessions and replay each original response When merchants share a key")
    void shouldCreateIndependentSessionsAndReplayWhenMerchantsShareAKey() {
        // Given
        String key = newKey();
        var first = createSession(MERCHANT, key, purchase());
        var second = createSession("other-merchant", key, purchase());

        // When
        var firstReplay = createSession(MERCHANT, key, purchase());
        var secondReplay = createSession("other-merchant", key, purchase());

        // Then
        assertThat(first.sessionId()).isNotEqualTo(second.sessionId());
        assertThat(firstReplay).isEqualTo(first);
        assertThat(secondReplay).isEqualTo(second);
    }

    @Test
    @DisplayName(
            "Should accept the corrected replacement When its key was previously rejected for a stale version")
    void shouldAcceptCorrectedReplacementWhenKeyWasRejectedForStaleVersion() {
        // Given
        var original = createSession(MERCHANT, newKey(), purchase());
        String key = newKey();
        var replacement = new ChangePurchaseRequest(15000, "BRL", null, null);
        assertProblem(
                replace(MERCHANT, original.sessionId(), key)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .body(replacement)
                        .exchange(),
                409,
                "PAYMENT_SESSION_VERSION_MISMATCH");

        // When
        var response =
                replace(MERCHANT, original.sessionId(), key)
                        .header(HttpHeaders.IF_MATCH, "\"1\"")
                        .body(replacement)
                        .exchange();

        // Then
        var changed =
                response.expectStatus()
                        .isOk()
                        .expectBody(PaymentSessionResponse.class)
                        .returnResult()
                        .getResponseBody();
        assertThat(changed).isNotNull();
        assertThat(changed.version()).isEqualTo(2);
        assertThat(changed.purchase().amountMinor()).isEqualTo(15000);
        assertThat(read(MERCHANT, original.sessionId())).isEqualTo(changed);
    }

    @Test
    @DisplayName(
            "Should return a business code and correlation identifier When an application rule rejects the request")
    void shouldReturnProblemDetailsWhenApplicationRuleRejectsRequest() {
        // Given
        String key = newKey();
        var original = createSession(MERCHANT, key, purchase());

        // When
        var response =
                create(MERCHANT, key, new CreatePaymentSessionRequest(20000, "BRL", null, null));

        // Then
        assertProblem(response, 409, "IDEMPOTENCY_KEY_CONFLICT");
        assertThat(read(MERCHANT, original.sessionId())).isEqualTo(original);
    }

    private RestTestClient.ResponseSpec create(
            String merchant, String key, CreatePaymentSessionRequest purchase) {
        return client.post()
                .uri("/v1/sessions")
                .header("X-Merchant-Id", merchant)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(purchase)
                .exchange();
    }

    private PaymentSessionResponse createSession(
            String merchant, String key, CreatePaymentSessionRequest purchase) {
        return create(merchant, key, purchase)
                .expectStatus()
                .isCreated()
                .expectBody(PaymentSessionResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private RestTestClient.RequestBodySpec replace(String merchant, String sessionId, String key) {
        return client.put()
                .uri("/v1/sessions/{id}", sessionId)
                .header("X-Merchant-Id", merchant)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private PaymentSessionResponse read(String merchant, String sessionId) {
        return client.get()
                .uri("/v1/sessions/{id}", sessionId)
                .header("X-Merchant-Id", merchant)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(PaymentSessionResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private void assertProblem(RestTestClient.ResponseSpec response, int status, String code) {
        var problem =
                response.expectStatus()
                        .isEqualTo(status)
                        .expectHeader()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                        .expectBody(ProblemDetail.class)
                        .returnResult()
                        .getResponseBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getStatus()).isEqualTo(status);
        assertThat(problem.getProperties()).containsEntry("code", code);
        assertThat(problem.getProperties().get("correlationId")).isInstanceOf(String.class);
        assertThat((String) problem.getProperties().get("correlationId")).isNotBlank();
    }

    private CreatePaymentSessionRequest purchase() {
        return new CreatePaymentSessionRequest(10000, "BRL", null, null);
    }

    private String newKey() {
        return UUID.randomUUID().toString();
    }
}
