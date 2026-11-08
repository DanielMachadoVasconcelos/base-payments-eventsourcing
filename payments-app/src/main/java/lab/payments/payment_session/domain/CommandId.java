package lab.payments.payment_session.domain;

import java.util.UUID;

public record CommandId(String value) {
    public static CommandId newId() {
        return new CommandId(UUID.randomUUID().toString());
    }
}
