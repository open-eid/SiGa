package ee.openeid.siga.monitoring;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.actuate.health.DefaultHealthIndicatorRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicatorRegistry;
import org.springframework.boot.actuate.health.Status;

@RunWith(MockitoJUnitRunner.class)
public class HeartbeatEndpointTest {

    private HeartbeatEndpoint endpoint;

    private final HealthIndicatorRegistry healthIndicatorRegistry = new DefaultHealthIndicatorRegistry();

    @Mock
    private IgniteHealthIndicator igniteHealthIndicator;
    @Mock
    private SivaHealthIndicator sivaHealthIndicator;

    @Before
    public void beforeTests() {
        healthIndicatorRegistry.register("ignite", igniteHealthIndicator);
        healthIndicatorRegistry.register("siva", sivaHealthIndicator);
        endpoint = new HeartbeatEndpoint(healthIndicatorRegistry);
        Mockito.when(igniteHealthIndicator.health()).thenReturn(Health.up().build());
        Mockito.when(sivaHealthIndicator.health()).thenReturn(Health.up().build());
    }

    @Test
    public void allApplicationAreWorking() {
        Assert.assertEquals(2, healthIndicatorRegistry.getAll().values().size());
        Status status = endpoint.heartbeat();
        Assert.assertEquals(Status.UP, status);
    }

    @Test
    public void cacheIsDown() {
        Mockito.when(igniteHealthIndicator.health()).thenReturn(Health.down().build());
        Status status = endpoint.heartbeat();
        Assert.assertEquals(Status.DOWN, status);
    }

    @Test
    public void sivaIsDown() {
        Mockito.when(sivaHealthIndicator.health()).thenReturn(Health.down().build());
        Status status = endpoint.heartbeat();
        Assert.assertEquals(Status.DOWN, status);
    }
}
