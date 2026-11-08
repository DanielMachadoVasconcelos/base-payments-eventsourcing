package lab.simulator;

import java.util.Map;
import lab.common.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SimulatorController {
    private final SimulatorService simulatorService;

    // Explicit binding for the phase, merchant/key headers and request body.
    @SuppressWarnings("checkstyle:ParameterNumber")
    @PostMapping("/sim/{phase}")
    public ResponseEntity<?> execute(
            @PathVariable String phase,
            @RequestHeader("X-Merchant-Id") String merchantId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> requestBody) {
        var key =
                new SimulatorOperationKey(
                        ApiValues.merchant(merchantId),
                        SimulationPhase.parse(phase),
                        ApiValues.idempotency(idempotencyKey));
        var result = simulatorService.execute(new SimulateOperation(key, requestBody));
        return ResponseEntity.status((int) Json.longValue(result, "httpStatus"))
                .body(Json.objectValue(result, "response"));
    }

    @GetMapping("/sim/results/{phase}/{key}")
    public Map<String, Object> result(
            @PathVariable String phase,
            @PathVariable("key") String idempotencyKey,
            @RequestHeader("X-Merchant-Id") String merchantId) {
        return Dynamo.clean(
                simulatorService.result(
                        new SimulatorOperationKey(
                                ApiValues.merchant(merchantId),
                                SimulationPhase.parse(phase),
                                idempotencyKey)));
    }
}
