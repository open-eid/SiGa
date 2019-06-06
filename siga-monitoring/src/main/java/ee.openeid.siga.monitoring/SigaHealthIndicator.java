package ee.openeid.siga.monitoring;

import ee.openeid.siga.session.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

@Component
public class SigaHealthIndicator implements HealthIndicator {

    @Autowired
    private SessionService sessionService;

    @Autowired
    JdbcTemplate template;

    @Override
    public Health health() {

        Health dbHealth = getDatabaseHealthBuilder().build();
        Health igniteHealth = getIgniteHealthBuilder().build();

        return getHealthBuilder(dbHealth, igniteHealth)
                .withDetail("ignite", igniteHealth)
                .withDetail("db", dbHealth)
                .build();
    }

    private Health.Builder getHealthBuilder(Health dbHealth, Health igniteHealth) {
        if (Status.DOWN == dbHealth.getStatus() || Status.DOWN == igniteHealth.getStatus()) {
            return Health.down();
        } else if (Status.UNKNOWN == dbHealth.getStatus() || Status.UNKNOWN == igniteHealth.getStatus()) {
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

    private Health.Builder getDatabaseHealthBuilder() {
        Health.Builder dbBuilder = new Health.Builder();
        List<Object> results = getQueryResult();

        if (Status.UP == getDatabaseStatus(results.size())) {
            dbBuilder.up().withDetail("database", Objects.requireNonNull(template.execute(this::getProduct)));
            dbBuilder.withDetail("hello", results.size());
        } else {
            dbBuilder.status(Status.DOWN);
        }
        return dbBuilder;
    }

    private List<Object> getQueryResult() {
        return template.query("select 1 from dual",
                new SingleColumnRowMapper<>());
    }

    private Status getIgniteStatus(int size) {
        if (size > 0) {
            return Status.UP;
        }
        return Status.UNKNOWN;
    }

    private Status getDatabaseStatus(int size) {
        if (size == 1) {
            return Status.UP;
        }
        return Status.DOWN;
    }


    private String getProduct(Connection connection) throws SQLException {
        return connection.getMetaData().getDatabaseProductName();
    }

}
