package ee.openeid.siga.auth.session;

import ee.openeid.siga.auth.model.SigaService;
import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.auth.repository.ServiceRepository;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.session.spi.ContainerExpiredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContainerConnectionCleanupListenerTest {

    @Mock
    private ConnectionRepository connectionRepository;
    @Mock
    private ServiceRepository serviceRepository;
    @InjectMocks
    private ContainerConnectionCleanupListener listener;

    @Test
    void shouldDoNothing_WhenSessionIdIsNull() {
        listener.onContainerExpired(new ContainerExpiredEvent(null));

        verifyNoInteractions(connectionRepository, serviceRepository);
    }

    @Test
    void shouldDeleteConnections_WhenServiceFound() {
        SigaService service = new SigaService();
        service.setId(42);
        when(serviceRepository.findByUuid("service-uuid-123")).thenReturn(Optional.of(service));
        when(connectionRepository.deleteByContainerIdAndServiceId("container-id-abc", 42)).thenReturn(3);

        listener.onContainerExpired(new ContainerExpiredEvent("v1_service-uuid-123_container-id-abc"));

        verify(connectionRepository).deleteByContainerIdAndServiceId("container-id-abc", 42);
    }

    @Test
    void shouldNotDeleteConnections_WhenServiceNotFound() {
        when(serviceRepository.findByUuid("unknown-uuid")).thenReturn(Optional.empty());

        listener.onContainerExpired(new ContainerExpiredEvent("v1_unknown-uuid_container-id-abc"));

        verifyNoInteractions(connectionRepository);
    }

    @Test
    void shouldThrowInvalidSessionDataException_WhenSessionIdMalformed() {
        ContainerExpiredEvent event = new ContainerExpiredEvent("only-two_parts");

        assertThrows(InvalidSessionDataException.class, () -> listener.onContainerExpired(event));

        verifyNoInteractions(connectionRepository, serviceRepository);
    }
}
