package ee.openeid.siga.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthIndicatorRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomActuatorConfiguration {

    @Autowired
    private HealthIndicatorRegistry healthIndicatorRegistry;

    @Bean
    public HeartbeatEndpoint heartbeatEndpoint() {
        return new HeartbeatEndpoint(healthIndicatorRegistry);
    }
}
