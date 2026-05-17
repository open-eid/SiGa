package ee.openeid.siga.monitoring;

import ee.openeid.siga.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class SessionStorageHealthIndicatorTest {

    private SessionStorageHealthIndicator indicator;

    @Mock
    private SessionService sessionService;

    @BeforeEach
    void beforeTests() {
        indicator = new SessionStorageHealthIndicator(sessionService);
    }

    @Test
    void downWhenCacheSizeFails() {
        Mockito.when(sessionService.getCacheSize()).thenThrow(new RuntimeException("Backend unavailable"));
        Health health = indicator.health();
        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void upWithActiveContainers() {
        Mockito.when(sessionService.getCacheSize()).thenReturn(2L);
        Health health = indicator.health();
        assertEquals(Status.UP, health.getStatus());
    }
}
