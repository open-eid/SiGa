package ee.openeid.siga.session;

import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.session.configuration.SessionStorageProperties;
import ee.openeid.siga.session.spi.SessionRemovedEvent;
import ee.openeid.siga.session.spi.SessionStorage;
import ee.openeid.siga.session.spi.SessionUpdatedEvent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionService {
    @NonNull
    private final SessionStorage sessionStorage;
    @NonNull
    private final ApplicationEventPublisher eventPublisher;
    @NonNull
    private final SessionStorageProperties properties;

    public Session getContainer(String containerId) {
        return getContainerBySessionId(getSessionId(containerId));
    }

    public Session getContainerBySessionId(String sessionId) {
        Session container = sessionStorage.get(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        log.debug("Found container with container ID [{}]", container.getSessionId());
        return container;
    }

    /**
     * Reads the session without refreshing its TTL — for background-driven paths (status
     * reprocessor, compactors) that must not keep otherwise-idle sessions alive. Use
     * {@link #getContainerBySessionId} from user-driven paths instead.
     */
    public Session peekContainerBySessionId(String sessionId) {
        Session container = sessionStorage.peek(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        log.debug("Peeked container with container ID [{}]", container.getSessionId());
        return container;
    }

    public void update(Session session) {
        sessionStorage.update(session);
        eventPublisher.publishEvent(new SessionUpdatedEvent(session));
    }

    public void removeByContainerId(String containerId) {
        removeBySessionId(getSessionId(containerId));
    }

    public void removeBySessionId(String sessionId) {
        sessionStorage.remove(sessionId);
        eventPublisher.publishEvent(new SessionRemovedEvent(sessionId));
    }

    public long getCacheSize() {
        return sessionStorage.size();
    }

    public String getSessionId(String containerId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new TechnicalException("No authenticated user in security context");
        }
        String user = authentication.getName();
        return properties.applicationCacheVersion() + "_" + user + "_" + containerId;
    }

    public static String parseServiceUuid(String sessionId) {
        return parsePart(sessionId, 1);
    }

    public static String parseContainerId(String sessionId) {
        return parsePart(sessionId, 2);
    }

    private static String parsePart(String sessionId, int index) {
        String[] parts = sessionId.split("_");
        if (parts.length != 3) {
            throw new InvalidSessionDataException("Invalid sessionId: " + sessionId);
        }
        return parts[index];
    }
}
