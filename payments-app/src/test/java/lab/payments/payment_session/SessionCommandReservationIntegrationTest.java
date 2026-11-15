package lab.payments.payment_session;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lab.common.Dynamo;
import lab.common.Json;
import lab.payments.payment_session.application.*;
import lab.payments.payment_session.domain.*;
import lab.payments.payment_session.infrastructure.DynamoDbSessionCommandReservations;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

@Tag("compose")
@SpringBootTest
class SessionCommandReservationIntegrationTest {
    private static final String TABLE =
            "payments_reservations_" + UUID.randomUUID().toString().replace("-", "");
    private static final MerchantId MERCHANT = new MerchantId("reservation-merchant");
    @Autowired private Dynamo dynamo;
    @Autowired private SessionCommandReservations reservations;
    @Autowired private CreatePaymentSessionService creation;
    @Autowired private ChangePurchaseService changes;
    @Autowired private PaymentSessionEventStore eventStore;
    @Autowired private PurchasePayloadStore payloads;
    @Autowired private PaymentSessionResultMapper resultMapper;

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
            "Should fence the old writer and preserve the new reservation When an expired command is taken over")
    void shouldFenceTheOldWriterAndPreserveTheNewReservationWhenAnExpiredCommandIsTakenOver() {
        // Given
        var session =
                creation.create(
                        new CreatePaymentSessionCommand(
                                MERCHANT, new Purchase(10000, "BRL", "", Map.of()), newKey()));
        var request = request(newKey());
        var original = reservations.acquire(request).orElseThrow();
        var stale = prepare(session.sessionId(), original);
        expire(original);

        // When
        var independentRepository = new DynamoDbSessionCommandReservations(dynamo, 30000);
        var successor = independentRepository.acquire(request).orElseThrow();
        reservations.release(original);

        // Then
        assertThat(dynamo.get(partition(request), "PROCESSING").get("leaseOwner"))
                .isEqualTo(successor.owner());
        assertThatThrownBy(() -> eventStore.append(stale.aggregate(), stale.completion()))
                .isInstanceOf(TransactionCanceledException.class);
        assertThat(dynamo.query(sessionPartition(session.sessionId()), "EVT#")).hasSize(1);
        assertThat(dynamo.get(sessionPartition(session.sessionId()), "HEAD").get("version"))
                .isEqualTo(1L);
        assertThat(dynamo.get(sessionPartition(session.sessionId()), "META").get("version"))
                .isEqualTo(1L);
        assertThat(eventStore.previouslyCompletedResponse(request)).isEmpty();

        var recovered = prepare(session.sessionId(), successor);
        var result = eventStore.append(recovered.aggregate(), recovered.completion());
        assertThat(result.version()).isEqualTo(2);
        assertThat(dynamo.query(sessionPartition(session.sessionId()), "EVT#")).hasSize(2);
        assertThat(eventStore.previouslyCompletedResponse(request)).contains(result);
        assertThat(dynamo.get(partition(request), "PROCESSING")).isNull();
        assertThat(reservations.acquire(request)).isEmpty();
    }

    @Test
    @DisplayName(
            "Should reject an expired writer When its reservation has elapsed without a successor")
    void shouldRejectAnExpiredWriterWhenItsReservationHasElapsedWithoutASuccessor() {
        // Given
        var session =
                creation.create(
                        new CreatePaymentSessionCommand(
                                MERCHANT, new Purchase(10000, "BRL", "", Map.of()), newKey()));
        var reservation = reservations.acquire(request(newKey())).orElseThrow();
        var pending = prepare(session.sessionId(), reservation);
        expire(reservation);

        // When / Then
        assertThatThrownBy(() -> eventStore.append(pending.aggregate(), pending.completion()))
                .isInstanceOf(TransactionCanceledException.class);
        assertThat(dynamo.query(sessionPartition(session.sessionId()), "EVT#")).hasSize(1);
        assertThat(eventStore.previouslyCompletedResponse(reservation.request())).isEmpty();
    }

    @Test
    @DisplayName(
            "Should release the reservation for correction When a purchase replacement is rejected")
    void shouldReleaseTheReservationForCorrectionWhenAPurchaseReplacementIsRejected() {
        // Given
        var session =
                creation.create(
                        new CreatePaymentSessionCommand(
                                MERCHANT, new Purchase(10000, "BRL", "", Map.of()), newKey()));
        String key = newKey();
        var identity =
                new PaymentSessionIdentity(MERCHANT, new PaymentSessionId(session.sessionId()));
        var purchase = new Purchase(15000, "BRL", "", Map.of());
        var invalid =
                new ChangePurchaseCommand(new SessionWriteContext(identity, key, 0), purchase);

        // When / Then
        assertThatThrownBy(() -> changes.change(invalid))
                .isInstanceOf(PaymentSessionVersionMismatchException.class);
        assertThat(dynamo.get(partition(request(key)), "PROCESSING")).isNull();
        assertThat(dynamo.get(partition(request(key)), "RESULT")).isNull();
        var corrected =
                changes.change(
                        new ChangePurchaseCommand(
                                new SessionWriteContext(identity, key, 1), purchase));
        assertThat(corrected.version()).isEqualTo(2);
        assertThat(dynamo.query(sessionPartition(session.sessionId()), "EVT#")).hasSize(2);
    }

    private PendingReplacement prepare(String sessionId, SessionCommandReservation reservation) {
        var identity = new PaymentSessionIdentity(MERCHANT, new PaymentSessionId(sessionId));
        var aggregate = eventStore.load(identity).orElseThrow();
        var purchase = new Purchase(15000, "BRL", "", Map.of());
        String payloadId = payloads.store(MERCHANT, purchase);
        aggregate.changePurchase(
                new ChangeSessionPurchase(
                        PurchaseRevision.of(purchase, payloadId),
                        1,
                        new CommandMetadata(CommandId.newId(), Instant.now())),
                new SessionExpirationPolicy(Duration.ofHours(48)));
        var result =
                resultMapper.toStoredResult(
                        PaymentSessionView.from(aggregate, purchase), payloadId);
        return new PendingReplacement(
                aggregate, new CompletedSessionCommand(reservation.request(), result, reservation));
    }

    private void expire(SessionCommandReservation reservation) {
        dynamo.client.updateItem(
                builder ->
                        builder.tableName(TABLE)
                                .key(Dynamo.key(partition(reservation.request()), "PROCESSING"))
                                .updateExpression("SET leaseUntil = :expired")
                                .expressionAttributeValues(
                                        Map.of(":expired", Dynamo.numberAttribute(0))));
    }

    private IdempotencyRequest request(String key) {
        return new IdempotencyRequest(
                new IdempotencyScope(MERCHANT, "CHANGE_PURCHASE", "v1"),
                key,
                Json.fingerprint(
                        Map.of(
                                "purchase",
                                new Purchase(15000, "BRL", "", Map.of()),
                                "expectedVersion",
                                1L)));
    }

    private String partition(IdempotencyRequest request) {
        return "M#"
                + MERCHANT.value()
                + "#IDEMP#"
                + Json.sha256("CHANGE_PURCHASE:v1:" + request.key());
    }

    private String sessionPartition(String sessionId) {
        return "M#" + MERCHANT.value() + "#S#" + sessionId;
    }

    private String newKey() {
        return UUID.randomUUID().toString();
    }

    private record PendingReplacement(
            PaymentSessionAggregate aggregate, CompletedSessionCommand completion) {}
}
