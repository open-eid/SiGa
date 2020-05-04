package ee.openeid.siga.monitoring;

import ee.openeid.siga.session.SessionService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

@RunWith(MockitoJUnitRunner.class)
public class IgniteHealthIndicatorTest {


    private IgniteHealthIndicator igniteHealthIndicator;

    @Mock
    private SessionService sessionService;

    @Before
    public void beforeTests() {
        igniteHealthIndicator = new IgniteHealthIndicator(sessionService);
    }

    @Test
    public void igniteDownStatus() {
        Mockito.when(sessionService.getCacheSize()).thenThrow(new RuntimeException("Invalid ignite session"));
        Health health = igniteHealthIndicator.health();
        Assert.assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    public void igniteUpStatus() {
        Mockito.when(sessionService.getCacheSize()).thenReturn(2);
        Health health = igniteHealthIndicator.health();
        Assert.assertEquals(Status.UP, health.getStatus());
    }
}
