package lab.simulator;

import lab.common.PaymentValidationException;

public enum SimulationPhase {
    AUTHORIZE,
    CONFIRM,
    ORDER,
    WEBHOOK;

    public static SimulationPhase parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new PaymentValidationException("UNKNOWN_PHASE", "Unknown simulator phase");
        }
    }
}
