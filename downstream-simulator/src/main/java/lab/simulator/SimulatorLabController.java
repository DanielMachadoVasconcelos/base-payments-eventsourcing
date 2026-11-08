package lab.simulator;

import java.time.Instant;
import java.util.*;
import lab.common.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile({"local", "test"})
@RestController
@RequestMapping("/lab")
@RequiredArgsConstructor
public class SimulatorLabController {
    private final SimulatorService simulatorService;
    private final BusinessClock businessClock;

    @PostMapping("/faults")
    public void fault(
            @RequestHeader("X-Merchant-Id") String merchantId,
            @RequestBody Map<String, Object> requestBody) {
        simulatorService.fault(ApiValues.merchant(merchantId), requestBody);
    }

    @GetMapping("/sessions/{session}/effects")
    public Object effects(
            @RequestHeader("X-Merchant-Id") String merchantId, @PathVariable String session) {
        return simulatorService.effects(ApiValues.merchant(merchantId), session);
    }

    @PostMapping("/clock")
    public Object businessClock(@RequestBody Map<String, Object> requestBody) {
        if (Json.booleanValue(requestBody, "reset")) businessClock.reset();
        else businessClock.set(Instant.parse(Json.stringValue(requestBody, "instant")));
        return Map.of("instant", businessClock.instant().toString());
    }
}
