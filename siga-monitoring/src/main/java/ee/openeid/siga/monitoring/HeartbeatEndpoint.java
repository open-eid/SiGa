package ee.openeid.siga.monitoring;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicatorRegistry;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.util.Collection;
import java.util.Optional;

@ConditionalOnExpression("'${management.endpoints.web.exposure.include}'.contains('heartbeat')")
@Endpoint(id = "heartbeat", enableByDefault = false)
public class HeartbeatEndpoint {

    private HealthIndicatorRegistry healthIndicatorRegistry;

    public HeartbeatEndpoint(HealthIndicatorRegistry healthIndicatorRegistry) {
        this.healthIndicatorRegistry = healthIndicatorRegistry;
    }

    @ReadOperation
    public Status heartbeat() {
        return getAggregatedStatus(getHealthIndicatorStatuses());
    }

    private Collection<HealthIndicator> getHealthIndicatorStatuses() {
        return healthIndicatorRegistry.getAll().values();
    }

    private Status getAggregatedStatus(Collection<HealthIndicator> healthIndicators) {
        Optional<HealthIndicator> anyNotUp = healthIndicators.stream()
                .filter(healthIndicator -> !Status.UP.equals(healthIndicator.health().getStatus()))
                .findAny();
        return anyNotUp.isPresent() ? Status.DOWN : Status.UP;
    }


}
