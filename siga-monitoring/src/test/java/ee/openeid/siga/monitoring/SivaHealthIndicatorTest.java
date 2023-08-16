package ee.openeid.siga.monitoring;

import ee.openeid.siga.common.client.HttpGetClient;
import ee.openeid.siga.common.configuration.SivaClientConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SivaHealthIndicatorTest {

    @InjectMocks
    private SivaHealthIndicator healthIndicator;
    @Mock
    private HttpGetClient httpClient;
    @Mock
    private SivaClientConfigurationProperties configProperties;

    @Test
    void sivaIsUp() {
        when(httpClient.get("/monitoring/health", SivaHealthIndicator.HealthStatus.class)).thenReturn(new SivaHealthIndicator.HealthStatus("UP"));
        Health health = healthIndicator.health();
        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void sivaIsDown() {
        when(httpClient.get("/monitoring/health", SivaHealthIndicator.HealthStatus.class)).thenReturn(new SivaHealthIndicator.HealthStatus("DOWN"));
        Health health = healthIndicator.health();
        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void couldNotConnectToSiva() {
        when(httpClient.get("/monitoring/health", SivaHealthIndicator.HealthStatus.class)).thenThrow(new RuntimeException("Network error"));
        Health health = healthIndicator.health();
        assertEquals(Status.DOWN, health.getStatus());
    }
}
