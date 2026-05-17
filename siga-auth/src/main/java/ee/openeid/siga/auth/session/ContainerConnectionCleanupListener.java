package ee.openeid.siga.auth.session;

import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.auth.repository.ServiceRepository;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.session.spi.ContainerExpiredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Deletes auth connection rows when a container session expires. Reacts to any
 * {@link ContainerExpiredEvent} the active backend publishes (Ignite or Redis).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerConnectionCleanupListener {
    private final ConnectionRepository connectionRepository;
    private final ServiceRepository serviceRepository;

    @Async
    @EventListener
    public void onContainerExpired(ContainerExpiredEvent event) {
        String sessionId = event.sessionId();
        if (sessionId == null) {
            log.debug("Expired session had no sessionId. No connection rows to delete.");
            return;
        }
        String containerId = SessionService.parseContainerId(sessionId);
        String serviceUuid = SessionService.parseServiceUuid(sessionId);
        serviceRepository.findByUuid(serviceUuid).ifPresentOrElse(
                service -> {
                    int count = connectionRepository.deleteByContainerIdAndServiceId(containerId, service.getId());
                    log.debug("Deleted {} connection(s) for container {} of service {}", count, containerId, service.getId());
                },
                () -> log.debug("Service with UUID {} not found; nothing to delete.", serviceUuid));
    }
}
