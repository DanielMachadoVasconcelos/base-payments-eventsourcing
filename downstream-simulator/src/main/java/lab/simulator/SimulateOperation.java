package lab.simulator;

import java.util.Map;
import lab.common.PaymentValidationException;

public record SimulateOperation(SimulatorOperationKey key, Map<String, Object> request) {
    public SimulationTarget target() {
        String sessionId = lab.common.Json.stringValue(request, "sessionId");
        if (sessionId.isBlank()) {
            throw new PaymentValidationException("INVALID_SESSION", "sessionId required");
        }
        return new SimulationTarget(key.merchantId(), sessionId, key.phase());
    }
}
