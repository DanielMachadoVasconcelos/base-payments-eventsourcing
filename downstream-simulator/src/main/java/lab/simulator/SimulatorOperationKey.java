package lab.simulator;

import static lab.common.Json.sha256;

public record SimulatorOperationKey(
        String merchantId, SimulationPhase phase, String idempotencyKey) {
    public String partitionKey() {
        return "M#" + merchantId + "#RESULT#" + phase + "#" + sha256(idempotencyKey);
    }
}
