package lab.common;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Deliberately separate from System.currentTimeMillis(), used for leases and network timeouts. */
@Component
public class BusinessClock {
    private final AtomicReference<Instant> fixed = new AtomicReference<>();

    public Instant instant() {
        var value = fixed.get();
        return value == null ? Instant.now() : value;
    }

    public long millis() {
        return instant().toEpochMilli();
    }

    public void set(Instant instant) {
        fixed.set(instant);
    }

    public void reset() {
        fixed.set(null);
    }
}
