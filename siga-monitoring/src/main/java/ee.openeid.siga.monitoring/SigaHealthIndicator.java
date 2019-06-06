package ee.openeid.siga.monitoring;

import ee.openeid.siga.session.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SigaHealthIndicator implements HealthIndicator {

    @Autowired
    private SessionService sessionService;

    @Autowired
    JdbcTemplate template;

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
        } else if (Status.UNKNOWN == igniteHealth.getStatus()) {
            return Health.unknown();
        }
        return Health.up();
    }

    private Health.Builder getIgniteHealthBuilder() {
        Health.Builder dbBuilder = new Health.Builder();
        int igniteCacheSize = sessionService.getCacheSize();
        if (Status.UP == getIgniteStatus(igniteCacheSize)) {
            dbBuilder.up().withDetail("igniteActiveContainers", igniteCacheSize);
        } else {
            dbBuilder.unknown().withDetail("igniteActiveContainers", igniteCacheSize);
        }
        return dbBuilder;
    }

    private Status getIgniteStatus(int size) {
        if (size > 0) {
            return Status.UP;
        }
        return Status.UNKNOWN;
    }

}
