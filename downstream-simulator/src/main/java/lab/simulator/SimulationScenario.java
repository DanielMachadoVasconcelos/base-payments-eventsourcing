package lab.simulator;

import java.util.Map;

public enum SimulationScenario {
    ACCEPT,
    REJECT;

    public static SimulationScenario from(Map<String, Object> fault) {
        return "REJECT".equals(lab.common.Json.stringValue(fault, "mode")) ? REJECT : ACCEPT;
    }
}
