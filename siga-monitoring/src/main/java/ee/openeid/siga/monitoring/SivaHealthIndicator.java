package ee.openeid.siga.monitoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ee.openeid.siga.common.configuration.SivaClientConfigurationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class SivaHealthIndicator implements HealthIndicator {
    private static final String HEALTH_ENDPOINT = "/monitoring/health";

    private final RestTemplate restTemplate;
    private final SivaClientConfigurationProperties configProperties;

    @Autowired
    public SivaHealthIndicator(RestTemplate restTemplate, SivaClientConfigurationProperties configProperties) {
        this.restTemplate = restTemplate;
        this.configProperties = configProperties;
    }

    @Override
    public Health health() {
        return createSivaHealthBuilder().build();
    }

    private Health.Builder createSivaHealthBuilder() {
        try {
            HealthStatus response = restTemplate.getForObject(configProperties.getUrl() + HEALTH_ENDPOINT, HealthStatus.class);
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HealthStatus {
        private String status;

        public HealthStatus() {
        }

        public HealthStatus(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
