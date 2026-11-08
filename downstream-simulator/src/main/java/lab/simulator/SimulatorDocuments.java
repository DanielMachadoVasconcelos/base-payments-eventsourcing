package lab.simulator;

import java.util.Map;
import lombok.Builder;

final class SimulatorDocuments {
    private SimulatorDocuments() {}

    record Ledger(
            String hash,
            int httpStatus,
            Map<String, Object> response,
            String sessionId,
            String phase) {}

    record Effect(
            String phase,
            Map<String, Object> request,
            Map<String, Object> response,
            int httpStatus) {}

    @Builder
    record Authorization(
            String status,
            String issuerAuthorizationId,
            long acceptedAt,
            long issuerExpiresAt,
            Map<String, Object> purchase) {}

    record Confirmation(String status, String authorizationId, long confirmedAt) {}

    @Builder
    record Order(
            String status,
            String orderId,
            String sessionId,
            String authorizationId,
            Map<String, Object> purchase) {}

    record Webhook(String status, String deliveryId, long receivedAt) {}
}
