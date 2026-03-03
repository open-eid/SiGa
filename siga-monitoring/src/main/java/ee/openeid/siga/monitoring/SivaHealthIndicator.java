package ee.openeid.siga.monitoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ee.openeid.siga.common.client.HttpGetClient;
import ee.openeid.siga.common.configuration.SivaClientConfigurationProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SivaHealthIndicator implements HealthIndicator {
    private static final String HEALTH_ENDPOINT = "/monitoring/health";
    private final HttpGetClient sivaHttpClient;
    private final SivaClientConfigurationProperties configProperties;
    @Override
    public Health health() {
        return createSivaHealthBuilder().build();
    }

    private Health.Builder createSivaHealthBuilder() {
        try {
            HealthStatus response = sivaHttpClient.get(HEALTH_ENDPOINT, HealthStatus.class);
            if (response == null) {
                throw new IllegalStateException("Invalid health status");
            }
            return getHealthBuilder(response);
        } catch (Exception e) {
            log.error("Failed to establish connection to '" + configProperties.getUrl() + HEALTH_ENDPOINT + "' > " + e.getMessage());
            return Health.down();
        }
    }

    private Health.Builder getHealthBuilder(HealthStatus health) {
        if (Status.UP.toString().equals(health.getStatus())) {
            return Health.up();
        }
        return Health.down();
    }

    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HealthStatus {
        private String status;

    }
}
