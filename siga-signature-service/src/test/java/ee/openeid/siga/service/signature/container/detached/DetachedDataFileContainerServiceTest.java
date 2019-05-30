package ee.openeid.siga.service.signature.container.detached;

import ee.openeid.siga.common.Signature;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.session.SessionResult;
import ee.openeid.siga.session.SessionService;
import org.apache.commons.lang3.StringUtils;
import org.digidoc4j.Configuration;
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;

import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_ID;
import static ee.openeid.siga.service.signature.test.RequestUtil.SIGNED_HASHCODE;
import static org.mockito.ArgumentMatchers.any;

@RunWith(MockitoJUnitRunner.class)
public class DetachedDataFileContainerServiceTest {

    @InjectMocks
    DetachedDataFileContainerService containerService;

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
        containerService.setConfiguration(Configuration.of(Configuration.Mode.TEST));
    }

    @Test
    public void successfulCreateContainer() {
        List<ee.openeid.siga.common.HashcodeDataFile> hashcodeDataFiles = RequestUtil.createHashcodeDataFiles();
        containerService.setSessionService(sessionService);
        String containerId = containerService.createContainer(hashcodeDataFiles);
        Assert.assertFalse(StringUtils.isBlank(containerId));
    }

    @Test
    public void successfulUploadContainer() throws IOException, URISyntaxException {
        String container = new String(Base64.getEncoder().encode(TestUtil.getFileInputStream(SIGNED_HASHCODE).readAllBytes()));
        String containerId = containerService.uploadContainer(container);
        Assert.assertFalse(StringUtils.isBlank(containerId));
    }

    @Test
    public void successfulGetContainer() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createDetachedDataFileSessionHolder());
        String container = containerService.getContainer(CONTAINER_ID);
        Assert.assertFalse(StringUtils.isBlank(container));
    }

    @Test
    public void successfulGetSignatures() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createDetachedDataFileSessionHolder());
        List<Signature> signatures = containerService.getSignatures(CONTAINER_ID);
        Assert.assertEquals("id-a9fae00496ae203a6a8b92adbe762bd3", signatures.get(0).getId());
        Assert.assertEquals("LT", signatures.get(0).getSignatureProfile());
        Assert.assertFalse(StringUtils.isBlank(signatures.get(0).getGeneratedSignatureId()));
        Assert.assertEquals("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE", signatures.get(0).getSignerInfo());
    }

    @Test
    public void successfulCloseSession() {
        String result = containerService.closeSession(CONTAINER_ID);
        Assert.assertEquals(SessionResult.OK.name(), result);
    }

}
