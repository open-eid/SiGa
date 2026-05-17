package ee.openeid.siga.monitoring;

import ee.openeid.siga.session.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports the active container count from whichever {@code SessionStorage} is wired (Ignite or
 * Redis). Replaces the Ignite-specific {@code IgniteHealthIndicator}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionStorageHealthIndicator implements HealthIndicator {
    private final SessionService sessionService;

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        try {
            builder.up().withDetail("activeContainers", sessionService.getCacheSize()); // TODO: Do we need to show nr. of containers in health endpoint? Is this actually used?
        } catch (Exception e) {
            builder.down().withDetail("activeContainers", 0);
        }
        return builder.build();
    }
}
