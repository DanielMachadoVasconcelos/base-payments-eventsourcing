package lab.payments.payment_session.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import lab.common.PaymentValidationException;
import lab.payments.payment_session.application.PaymentSessionView;
import lab.payments.payment_session.application.SessionWriteContext;
import lab.payments.payment_session.domain.PaymentSessionIdentity;
import lab.payments.payment_session.domain.Purchase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
    PaymentSessionRequestMapperImpl.class,
    PaymentSessionResponseMapperImpl.class,
    PaymentSessionInputConversions.class
})
class PaymentSessionMappingTest {
    @Autowired private PaymentSessionRequestMapper requestMapper;
    @Autowired private PaymentSessionResponseMapper responseMapper;
    @Autowired private PaymentSessionInputConversions conversions;

    @Test
    @DisplayName(
            "Should preserve defaults and identifiers When optional request fields are omitted")
    void shouldPreserveDefaultsAndIdentifiersWhenOptionalRequestFieldsAreOmitted() {
        // Given
        var request = new CreatePaymentSessionRequest(10000, "BRL", null, null);

        // When
        var command = requestMapper.create("merchant-a", "create-key", request);

        // Then
        assertThat(command.merchantId().value()).isEqualTo("merchant-a");
        assertThat(command.idempotencyKey()).isEqualTo("create-key");
        assertThat(command.purchase()).isEqualTo(new Purchase(10000, "BRL", "", Map.of()));
    }

    @Test
    @DisplayName(
            "Should preserve the expected version and full purchase When mapping a replacement request")
    void shouldPreserveTheExpectedVersionAndFullPurchaseWhenMappingAReplacementRequest() {
        // Given
        var attributes = Map.<String, Object>of("items", java.util.List.of("first", "second"));
        var request = new ChangePurchaseRequest(15000, "BRL", "cart-150", attributes);

        // When
        var command = requestMapper.change(context("\"2\""), request);

        // Then
        assertThat(command.context().identity().sessionId().value()).isEqualTo("session-a");
        assertThat(command.context().expectedVersion()).isEqualTo(2);
        assertThat(command.purchase())
                .isEqualTo(new Purchase(15000, "BRL", "cart-150", attributes));
    }

    @ParameterizedTest
    @MethodSource("invalidAmounts")
    @DisplayName("Should reject the amount When mapping an invalid purchase amount")
    void shouldRejectTheAmountWhenMappingAnInvalidPurchaseAmount(Object amountMinor) {
        // Given
        var request = new CreatePaymentSessionRequest(amountMinor, "BRL", null, null);

        // When / Then
        assertThatThrownBy(() -> requestMapper.create("merchant-a", "create-key", request))
                .isInstanceOfSatisfying(
                        PaymentValidationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_AMOUNT"));
    }

    static Stream<Object> invalidAmounts() {
        return Stream.of(null, 0, -1, 1.5, "not-a-number", "9223372036854775808");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"brl", "ZZZ"})
    @DisplayName("Should reject the currency When mapping an invalid currency")
    void shouldRejectTheCurrencyWhenMappingAnInvalidCurrency(String currency) {
        // Given
        var request = new CreatePaymentSessionRequest(10000, currency, null, null);

        // When / Then
        assertThatThrownBy(() -> requestMapper.create("merchant-a", "create-key", request))
                .isInstanceOfSatisfying(
                        PaymentValidationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_CURRENCY"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"merchant space", "../merchant"})
    @DisplayName("Should reject the merchant When mapping an invalid merchant identifier")
    void shouldRejectTheMerchantWhenMappingAnInvalidMerchantIdentifier(String merchantId) {
        // Given
        var request = new CreatePaymentSessionRequest(10000, "BRL", null, null);

        // When / Then
        assertThatThrownBy(() -> requestMapper.create(merchantId, "create-key", request))
                .isInstanceOfSatisfying(
                        PaymentValidationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_MERCHANT"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("Should reject the idempotency key When mapping an invalid key")
    void shouldRejectTheIdempotencyKeyWhenMappingAnInvalidKey(String idempotencyKey) {
        // Given
        var request = new CreatePaymentSessionRequest(10000, "BRL", null, null);

        // When / Then
        assertThatThrownBy(() -> requestMapper.create("merchant-a", idempotencyKey, request))
                .isInstanceOfSatisfying(
                        PaymentValidationException.class,
                        exception ->
                                assertThat(exception.code()).isEqualTo("INVALID_IDEMPOTENCY_KEY"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"not-a-version", "\"abc\""})
    @DisplayName("Should reject the ETag When mapping an invalid version header")
    void shouldRejectTheETagWhenMappingAnInvalidVersionHeader(String versionHeader) {
        // Given
        var request = new ChangePurchaseRequest(10000, "BRL", null, null);

        // When / Then
        assertThatThrownBy(() -> conversions.toExpectedVersion(versionHeader))
                .isInstanceOfSatisfying(
                        PaymentValidationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_ETAG"));
    }

    @Test
    @DisplayName(
            "Should preserve status ETag and purchase When mapping a session view to HTTP responses")
    void shouldPreserveStatusETagAndPurchaseWhenMappingASessionViewToHTTPResponses() {
        // Given
        var purchase = new Purchase(15000, "BRL", null, Map.of("customer", "Ana"));
        var session =
                new PaymentSessionView(
                        "session-a", purchase, 3, "OPEN", Instant.parse("2026-11-10T12:00:00Z"));

        var createdResponse = responseMapper.created(session);
        var currentResponse = responseMapper.ok(session);

        // Then
        assertThat(createdResponse.getStatusCode().value()).isEqualTo(201);
        assertThat(currentResponse.getStatusCode().value()).isEqualTo(200);
        assertThat(currentResponse.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(currentResponse.getBody()).isEqualTo(createdResponse.getBody());
        assertThat(currentResponse.getBody().purchase())
                .isEqualTo(new PurchaseResponse(15000, "BRL", "", purchase.attributes()));
    }

    private SessionWriteContext context(String versionHeader) {
        var identity =
                new PaymentSessionIdentity(
                        conversions.toMerchantId("merchant-a"),
                        conversions.toSessionId("session-a"));
        return new SessionWriteContext(
                identity,
                conversions.validatedIdempotencyKey("change-key"),
                conversions.toExpectedVersion(versionHeader));
    }
}
