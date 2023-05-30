package ee.openeid.siga.monitoring;

import ee.openeid.siga.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class IgniteHealthIndicatorTest {


    private IgniteHealthIndicator igniteHealthIndicator;

    @Mock
    private SessionService sessionService;

    @BeforeEach
    void beforeTests() {
        igniteHealthIndicator = new IgniteHealthIndicator(sessionService);
    }

    @Test
    void igniteDownStatus() {
        Mockito.when(sessionService.getCacheSize()).thenThrow(new RuntimeException("Invalid ignite session"));
        Health health = igniteHealthIndicator.health();
        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void igniteUpStatus() {
        Mockito.when(sessionService.getCacheSize()).thenReturn(2);
        Health health = igniteHealthIndicator.health();
        assertEquals(Status.UP, health.getStatus());
    }
}
