package ee.openeid.siga.session;

import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.util.UUIDGenerator;
import ee.openeid.siga.session.configuration.SessionStorageProperties;
import ee.openeid.siga.session.spi.SessionRemovedEvent;
import ee.openeid.siga.session.spi.SessionStorage;
import ee.openeid.siga.session.spi.SessionUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Backend-agnostic contract tests for {@link SessionService}. {@code SessionService} is a thin
 * delegation layer over {@link SessionStorage}; these tests exercise its behaviour against any
 * conforming backend. Concrete subclasses wire the backend and implement {@link #storage()} and
 * {@link #resetStorage()}.
 */
public abstract class SessionServiceTest {

    protected SessionService sessionService;
    protected ApplicationEventPublisher eventPublisher;

    protected abstract SessionStorage storage();

    protected abstract void resetStorage();

    @BeforeEach
    void setUp() {
        resetStorage();
        SessionStorageProperties properties = new SessionStorageProperties("v1");
        eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        sessionService = new SessionService(
                storage(),
                eventPublisher,
                properties);
        Authentication authentication = Mockito.mock(Authentication.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        when(authentication.getPrincipal()).thenReturn(defaultUserDetails());
        when(authentication.getName()).thenReturn("user_name");
    }

    @Test
    void shouldThrowResourceNotFound_WhenContainerMissing() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> sessionService.getContainer(UUIDGenerator.generateUUID())
        );
    }

    @Test
    void shouldFindContainer_WhenInserted() {
        String containerId = UUIDGenerator.generateUUID();
        sessionService.update(defaultSession(containerId));
        Session session = sessionService.getContainer(containerId);
        assertEquals("Client_name", session.getClientName());
        assertEquals("Service_name", session.getServiceName());
        assertEquals("Service_uuid", session.getServiceUuid());
        assertEquals("v1_user_name_" + containerId, session.getSessionId());
    }

    @Test
    void shouldIncreaseCacheSize_WhenSessionsInserted() {
        long initialCacheSize = sessionService.getCacheSize();
        sessionService.update(defaultSession(UUIDGenerator.generateUUID()));
        sessionService.update(defaultSession(UUIDGenerator.generateUUID()));
        assertEquals(initialCacheSize + 2, sessionService.getCacheSize());
    }

    @Test
    void shouldRestoreCacheSize_WhenContainerRemoved() {
        long initialCacheSize = sessionService.getCacheSize();
        String containerId = UUIDGenerator.generateUUID();
        sessionService.update(defaultSession(containerId));
        sessionService.removeByContainerId(containerId);
        assertEquals(initialCacheSize, sessionService.getCacheSize());
    }

    @Test
    void shouldPublishSessionUpdatedEvent_WhenUpdateCalled() {
        Session session = defaultSession(UUIDGenerator.generateUUID());
        sessionService.update(session);
        ArgumentCaptor<SessionUpdatedEvent> captor = ArgumentCaptor.forClass(SessionUpdatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertSame(session, captor.getValue().session());
    }

    @Test
    void shouldPublishSessionRemovedEvent_WhenRemoveBySessionIdCalled() {
        String containerId = UUIDGenerator.generateUUID();
        Session session = defaultSession(containerId);
        sessionService.update(session);
        sessionService.removeBySessionId(session.getSessionId());
        ArgumentCaptor<SessionRemovedEvent> captor = ArgumentCaptor.forClass(SessionRemovedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(session.getSessionId(), captor.getValue().sessionId());
    }

    @Test
    void shouldPublishSessionRemovedEvent_WhenRemoveByContainerIdCalled() {
        String containerId = UUIDGenerator.generateUUID();
        Session session = defaultSession(containerId);
        sessionService.update(session);
        sessionService.removeByContainerId(containerId);
        ArgumentCaptor<SessionRemovedEvent> captor = ArgumentCaptor.forClass(SessionRemovedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertEquals(session.getSessionId(), captor.getValue().sessionId());
    }

    @Test
    void shouldReturnContainer_WhenPeekContainerBySessionIdCalled() {
        String containerId = UUIDGenerator.generateUUID();
        Session session = defaultSession(containerId);
        sessionService.update(session);
        Session peeked = sessionService.peekContainerBySessionId(session.getSessionId());
        assertEquals(session.getSessionId(), peeked.getSessionId());
        assertEquals(session.getServiceUuid(), peeked.getServiceUuid());
    }

    @Test
    void shouldThrowResourceNotFound_WhenPeekContainerBySessionIdMissing() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> sessionService.peekContainerBySessionId("v1_user_name_" + UUIDGenerator.generateUUID())
        );
    }

    @Test
    void shouldReturnServiceUuid_WhenParseServiceUuidGivenValidSessionId() {
        assertEquals("svc-uuid", SessionService.parseServiceUuid("v1_svc-uuid_container-id"));
    }

    @Test
    void shouldReturnContainerId_WhenParseContainerIdGivenValidSessionId() {
        assertEquals("container-id", SessionService.parseContainerId("v1_svc-uuid_container-id"));
    }

    @Test
    void shouldThrowInvalidSessionData_WhenParseServiceUuidGivenMalformedSessionId() {
        assertThrows(InvalidSessionDataException.class, () -> SessionService.parseServiceUuid("only-two_parts"));
        assertThrows(InvalidSessionDataException.class, () -> SessionService.parseServiceUuid("too_many_parts_here"));
        assertThrows(InvalidSessionDataException.class, () -> SessionService.parseServiceUuid(""));
    }

    @Test
    void shouldThrowInvalidSessionData_WhenParseContainerIdGivenMalformedSessionId() {
        assertThrows(InvalidSessionDataException.class, () -> SessionService.parseContainerId("only-two_parts"));
        assertThrows(InvalidSessionDataException.class, () -> SessionService.parseContainerId("too_many_parts_here"));
        assertThrows(InvalidSessionDataException.class, () -> SessionService.parseContainerId("nounderscores"));
    }

    private static SigaUserDetails defaultUserDetails() {
        return SigaUserDetails.builder()
                .clientName("Client_name")
                .serviceName("Service_name")
                .serviceUuid("Service_uuid").build();
    }

    private Session defaultSession(String containerId) {
        String sessionId = sessionService.getSessionId(containerId);
        SigaUserDetails user = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return HashcodeContainerSession.builder()
                .sessionId(sessionId)
                .clientName(user.getClientName())
                .serviceName(user.getServiceName())
                .serviceUuid(user.getServiceUuid())
                .dataFiles(Collections.emptyList())
                .signatures(Collections.emptyList())
                .build();
    }
}
