package ee.openeid.siga.monitoring;

import ee.openeid.siga.session.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component
public class SigaHealthIndicator implements HealthIndicator {

    private SessionService sessionService;

    @Override
    public Health health() {
        Health igniteHealth = getIgniteHealthBuilder().build();
        return getHealthBuilder(igniteHealth)
                .withDetail("ignite", igniteHealth)
                .build();
    }

    private Health.Builder getHealthBuilder(Health igniteHealth) {
        if (Status.DOWN == igniteHealth.getStatus()) {
            return Health.down();
        }
        return Health.up();
    }

    private Health.Builder getIgniteHealthBuilder() {
        Health.Builder dbBuilder = new Health.Builder();
        try {
            int igniteCacheSize = sessionService.getCacheSize();
            dbBuilder.up().withDetail("igniteActiveContainers", igniteCacheSize);
        } catch (Exception e) {
            dbBuilder.down().withDetail("igniteActiveContainers", 0);
        }
        return dbBuilder;
    }

    @Autowired
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

}
