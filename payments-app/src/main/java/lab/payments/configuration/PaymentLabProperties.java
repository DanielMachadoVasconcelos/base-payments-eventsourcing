package lab.payments.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lab")
public record PaymentLabProperties(
        long synchronousWaitMs,
        long leaseMs,
        long heartbeatMs,
        int maxAttempts,
        long authorizationMs,
        long sessionMs,
        long acceptedSessionMs,
        long retentionMs,
        boolean workersEnabled) {}
