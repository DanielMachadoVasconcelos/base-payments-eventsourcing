package lab.payments.configuration;

import java.time.Duration;

import lab.payments.payment_session.domain.SessionExpirationPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

@Configuration
@EnableResilientMethods
public class PaymentSessionPolicies {
    @Bean
    public SessionExpirationPolicy sessionExpirationPolicy(PaymentLabProperties properties) {
        return new SessionExpirationPolicy(Duration.ofMillis(properties.sessionMs()));
    }
}
