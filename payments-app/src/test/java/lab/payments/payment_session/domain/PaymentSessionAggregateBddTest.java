package lab.payments.payment_session.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentSessionAggregateBddTest {
    private static final PaymentSessionIdentity IDENTITY =
            new PaymentSessionIdentity(
                    new MerchantId("merchant-a"), new PaymentSessionId("session-a"));
    private static final Instant CREATED_AT = Instant.parse("2026-10-18T12:00:00Z");
    private static final SessionExpirationPolicy POLICY =
            new SessionExpirationPolicy(Duration.ofHours(48));

    @Test
    @DisplayName(
            "Should restore the purchase version and recorded expiry without new events When replaying a purchase change")
    void
            shouldRestoreThePurchaseVersionAndRecordedExpiryWithoutNewEventsWhenReplayingAPurchaseChange() {
        // Given
        var session = createSession();
        var command = change(1, CREATED_AT.plusSeconds(1));
        // When
        session.changePurchase(command, POLICY);
        var replayed = PaymentSessionAggregate.rehydrate(IDENTITY, session.changes());
        // Then
        assertThat(replayed.version()).isEqualTo(2);
        assertThat(replayed.purchase().amountMinor()).isEqualTo(15000);
        assertThat(replayed.purchasePayloadId()).isEqualTo("payload-150");
        assertThat(replayed.expiresAt())
                .isEqualTo(POLICY.expiresAfter(command.metadata().occurredAt()));
        assertThat(replayed.createdAt()).isEqualTo(CREATED_AT);
        assertThat(replayed.changes()).isEmpty();
    }

    @Test
    @DisplayName(
            "Should preserve state without new events When a purchase change uses a stale version")
    void shouldPreserveStateWithoutNewEventsWhenAPurchaseChangeUsesAStaleVersion() {
        // Given
        var session = createSession();
        session.markChangesPersisted();
        // When / Then
        assertThatThrownBy(
                        () -> session.changePurchase(change(0, CREATED_AT.plusSeconds(1)), POLICY))
                .isInstanceOf(PaymentSessionVersionMismatchException.class);
        assertThat(session.version()).isEqualTo(1);
        assertThat(session.changes()).isEmpty();
    }

    @Test
    @DisplayName(
            "Should reject the purchase change When the session reaches its exact expiry instant")
    void shouldRejectThePurchaseChangeWhenTheSessionReachesItsExactExpiryInstant() {
        // Given
        var session = createSession();
        session.markChangesPersisted();
        // When / Then
        assertThatThrownBy(() -> session.changePurchase(change(1, session.expiresAt()), POLICY))
                .isInstanceOf(PaymentSessionExpiredException.class);
        assertThat(session.changes()).isEmpty();
    }

    @Test
    @DisplayName("Should preserve the original purchase revision When the purchase is replaced")
    void shouldPreserveTheOriginalPurchaseRevisionWhenThePurchaseIsReplaced() {
        // Given
        var session = createSession();
        var created = session.changes().getFirst();
        // When
        session.changePurchase(change(1, CREATED_AT.plusSeconds(1)), POLICY);
        // Then
        assertThat(created.snapshot().revision().summary().amountMinor()).isEqualTo(10000);
        assertThat(created.snapshot().revision().payloadId().value()).isEqualTo("payload-100");
        assertThat(session.purchasePayloadId()).isEqualTo("payload-150");
    }

    @Test
    @DisplayName("Should apply the supplied expiration policy When the purchase changes")
    void shouldApplyTheSuppliedExpirationPolicyWhenThePurchaseChanges() {
        // Given
        var session = createSession();
        var policy = new SessionExpirationPolicy(Duration.ofHours(49));
        var command = change(1, CREATED_AT.plusSeconds(10));
        // When
        session.changePurchase(command, policy);
        // Then
        assertThat(session.lastBusinessActivityAt()).isEqualTo(command.metadata().occurredAt());
        assertThat(session.expiresAt())
                .isEqualTo(command.metadata().occurredAt().plus(Duration.ofHours(49)));
    }

    private PaymentSessionAggregate createSession() {
        var command =
                new OpenPaymentSession(
                        IDENTITY,
                        PurchaseRevision.of(purchase(10000), "payload-100"),
                        new CommandMetadata(new CommandId("create-command"), CREATED_AT));
        return PaymentSessionAggregate.create(command, POLICY);
    }

    private ChangeSessionPurchase change(long expectedVersion, Instant occurredAt) {
        return new ChangeSessionPurchase(
                PurchaseRevision.of(purchase(15000), "payload-150"),
                expectedVersion,
                new CommandMetadata(new CommandId("change-command"), occurredAt));
    }

    private Purchase purchase(long amountMinor) {
        return new Purchase(amountMinor, "BRL", "cart", Map.of("customer", "Ana"));
    }
}
