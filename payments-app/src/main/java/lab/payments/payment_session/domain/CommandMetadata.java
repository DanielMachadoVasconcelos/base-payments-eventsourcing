package lab.payments.payment_session.domain;

import java.time.Instant;

public record CommandMetadata(CommandId commandId, Instant occurredAt) {}
