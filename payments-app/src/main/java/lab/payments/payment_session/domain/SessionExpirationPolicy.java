package lab.payments.payment_session.domain;

import java.time.Duration;
import java.time.Instant;

public record SessionExpirationPolicy(Duration inactivityTimeout) {
    public SessionExpirationPolicy {
        if (inactivityTimeout.isNegative() || inactivityTimeout.isZero()) {
            throw new IllegalArgumentException("Session inactivity timeout must be positive");
        }
    }

    public Instant expiresAfter(Instant activityAt) {
        return activityAt.plus(inactivityTimeout);
    }
}
