package ee.openeid.siga.session;


import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.session.HashcodeContainerSession;
import ee.openeid.siga.common.session.Session;
import ee.openeid.siga.common.util.UUIDGenerator;
import ee.openeid.siga.session.configuration.SessionConfigurationProperties;
import org.apache.ignite.Ignite;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;

import static org.mockito.Mockito.when;

@SpringBootTest(classes = {IgniteConfiguration.class})
@ActiveProfiles({"test"})
@RunWith(SpringRunner.class)
public class SessionServiceTest {
    private SessionService sessionService;

    @Autowired
    private Ignite ignite;

    @Before
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

    @Test(expected = ResourceNotFoundException.class)
    public void noContainerInSession() {
        sessionService.getContainer(UUIDGenerator.generateUUID());
    }

    @Test
    public void containerInsertedAndFound() {
        String containerId = UUIDGenerator.generateUUID();
        sessionService.update(createDefaultSession(containerId));
        Session session = sessionService.getContainer(containerId);
        Assert.assertEquals("Client_name", session.getClientName());
        Assert.assertEquals("Service_name", session.getServiceName());
        Assert.assertEquals("Service_uuid", session.getServiceUuid());
        Assert.assertEquals("v1_user_name_" + containerId, session.getSessionId());
    }

    @Test
    public void getMultipleSessionsCacheSize() {
        int initialCacheSize = sessionService.getCacheSize();
        sessionService.update(createDefaultSession(UUIDGenerator.generateUUID()));
        sessionService.update(createDefaultSession(UUIDGenerator.generateUUID()));
        int cacheSize = sessionService.getCacheSize();
        Assert.assertEquals(initialCacheSize + 2, cacheSize);
    }

    @Test
    public void removeContainerFromSession() {
        String containerId = UUIDGenerator.generateUUID();
        int initialCacheSize = sessionService.getCacheSize();
        sessionService.update(createDefaultSession(containerId));
        sessionService.remove(containerId);
        int cacheSize = sessionService.getCacheSize();
        Assert.assertEquals(initialCacheSize, cacheSize);
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
