package ee.openeid.siga.session;


import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.util.UUIDGenerator;
import ee.openeid.siga.session.configuration.SessionConfigurationProperties;
import org.apache.ignite.Ignite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = {IgniteConfiguration.class})
@ActiveProfiles({"test"})
public class SessionServiceTest {
    private SessionService sessionService;

    @Autowired
    private Ignite ignite;

    @BeforeEach
    public void setUp() {
        SessionConfigurationProperties sessionConfigurationProperties = new SessionConfigurationProperties();
        sessionConfigurationProperties.setApplicationCacheVersion("v1");
        sessionService = new SessionService(ignite, sessionConfigurationProperties);
        Authentication authentication = Mockito.mock(Authentication.class);
        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        when(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).thenReturn(createDefaultUserDetails());
        when(SecurityContextHolder.getContext().getAuthentication().getName()).thenReturn("user_name");
    }

    @Test
    public void noContainerInSession() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> sessionService.getContainer(UUIDGenerator.generateUUID())
        );
    }

    @Test
    public void containerInsertedAndFound() {
        String containerId = UUIDGenerator.generateUUID();
        sessionService.update(createDefaultSession(containerId));
        Session session = sessionService.getContainer(containerId);
        assertEquals("Client_name", session.getClientName());
        assertEquals("Service_name", session.getServiceName());
        assertEquals("Service_uuid", session.getServiceUuid());
        assertEquals("v1_user_name_" + containerId, session.getSessionId());
    }

    @Test
    public void getMultipleSessionsCacheSize() {
        int initialCacheSize = 0;
        try {
            initialCacheSize = sessionService.getCacheSize();
        } catch (NullPointerException e) {}
        sessionService.update(createDefaultSession(UUIDGenerator.generateUUID()));
        sessionService.update(createDefaultSession(UUIDGenerator.generateUUID()));
        int cacheSize = sessionService.getCacheSize();
        assertEquals(initialCacheSize + 2, cacheSize);
    }

    @Test
    public void removeContainerFromSession() {
        int initialCacheSize = 0;
        try {
            initialCacheSize = sessionService.getCacheSize();
        } catch (NullPointerException e) {}
        String containerId = UUIDGenerator.generateUUID();
        sessionService.update(createDefaultSession(containerId));
        sessionService.removeByContainerId(containerId);
        int cacheSize = sessionService.getCacheSize();
        assertEquals(initialCacheSize, cacheSize);
    }

    private SigaUserDetails createDefaultUserDetails() {
        return SigaUserDetails.builder()
                .clientName("Client_name")
                .serviceName("Service_name")
                .serviceUuid("Service_uuid").build();
    }

    private Session createDefaultSession(String containerId) {
        String sessionId = sessionService.getSessionId(containerId);
        SigaUserDetails authenticatedUser = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return HashcodeContainerSession.builder()
                .sessionId(sessionId)
                .clientName(authenticatedUser.getClientName())
                .serviceName(authenticatedUser.getServiceName())
                .serviceUuid(authenticatedUser.getServiceUuid())
                .dataFiles(Collections.emptyList())
                .signatures(Collections.emptyList())
                .build();
    }
}
