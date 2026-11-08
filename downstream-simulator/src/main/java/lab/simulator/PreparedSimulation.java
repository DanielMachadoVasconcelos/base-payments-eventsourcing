package lab.simulator;

record PreparedSimulation(
        SimulateOperation operation, SimulatedOutcome outcome, String fingerprint) {
    SimulatorDocuments.Ledger ledger() {
        return new SimulatorDocuments.Ledger(
                fingerprint,
                outcome.httpStatus(),
                outcome.response(),
                operation.target().sessionId(),
                operation.key().phase().name());
    }

    SimulatorDocuments.Effect effect() {
        return new SimulatorDocuments.Effect(
                operation.key().phase().name(),
                operation.request(),
                outcome.response(),
                outcome.httpStatus());
    }

    String sessionPartitionKey() {
        var target = operation.target();
        return "M#" + target.merchantId() + "#S#" + target.sessionId();
    }
}
