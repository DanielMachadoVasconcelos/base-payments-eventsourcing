package lab.simulator;

import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

/** A prepared response and its writes; no effect is committed until the enclosing transaction. */
record SimulatedOutcome(
        String effectId,
        int httpStatus,
        Map<String, Object> response,
        List<TransactWriteItem> effectWrites) {
    SimulatedOutcome(String effectId, int httpStatus, Map<String, Object> response) {
        this(effectId, httpStatus, response, List.of());
    }
}
