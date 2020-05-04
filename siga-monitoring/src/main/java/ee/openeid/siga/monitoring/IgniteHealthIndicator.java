package ee.openeid.siga.monitoring;

import ee.openeid.siga.session.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IgniteHealthIndicator implements HealthIndicator {

    private final SessionService sessionService;

    @Autowired
    public IgniteHealthIndicator(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public Health health() {
        return createIgniteHealthBuilder().build();
    }

    private Health.Builder createIgniteHealthBuilder() {
        Health.Builder builder = new Health.Builder();
        try {
            int igniteCacheSize = sessionService.getCacheSize();
            builder.up().withDetail("igniteActiveContainers", igniteCacheSize);
        } catch (Exception e) {
            builder.down().withDetail("igniteActiveContainers", 0);
        }
        return builder;
    }

}

