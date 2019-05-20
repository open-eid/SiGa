package ee.openeid.siga.service.signature;


import ee.openeid.siga.common.DataFile;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.session.SessionService;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class AttachedDataFileContainerServiceTest {


    @InjectMocks
    AttachedDataFileContainerService containerService;

    @Mock
    private SessionService sessionService;

    @Before
    public void setUp() {
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getPrincipal()).thenReturn(SigaUserDetails.builder()
                .clientName("client1")
                .serviceName("Testimine")
                .serviceUuid("a7fd7728-a3ea-4975-bfab-f240a67e894f").build());

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    public void successfulCreateContainer() {
        List<DataFile> dataFiles = RequestUtil.createDataFileListWithOneFile();
        containerService.setSessionService(sessionService);

        String containerId = containerService.createContainer(dataFiles);
        Assert.assertFalse(StringUtils.isBlank(containerId));
    }
}
