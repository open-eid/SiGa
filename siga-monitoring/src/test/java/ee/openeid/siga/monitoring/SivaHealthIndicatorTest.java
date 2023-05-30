package ee.openeid.siga.monitoring;

import ee.openeid.siga.common.configuration.SivaClientConfigurationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class SivaHealthIndicatorTest {

    @InjectMocks
    private SivaHealthIndicator healthIndicator;

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private SivaClientConfigurationProperties configProperties;

    @BeforeEach
    void init() {
        Mockito.when(restTemplate.getForObject(anyString(), any())).thenReturn(new SivaHealthIndicator.HealthStatus("UP"));
    }

    @Test
    void sivaIsUp() {
        Health health = healthIndicator.health();
        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void sivaIsDown() {
        Mockito.when(restTemplate.getForObject(anyString(), any())).thenReturn(new SivaHealthIndicator.HealthStatus("DOWN"));
        Health health = healthIndicator.health();
        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void couldNotConnectToSiva() {
        Mockito.when(restTemplate.getForObject(anyString(), any())).thenThrow(new RuntimeException("Network error"));
        Health health = healthIndicator.health();
        assertEquals(Status.DOWN, health.getStatus());
    }
}
