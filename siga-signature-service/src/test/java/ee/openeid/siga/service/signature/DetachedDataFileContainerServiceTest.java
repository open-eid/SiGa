package ee.openeid.siga.service.signature;

import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.session.SessionResult;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRequest;
import ee.openeid.siga.webapp.json.Signature;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

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

    @Test
    public void successfulCreateContainer() {
        CreateHashcodeContainerRequest request = RequestUtil.getHashcodeCreateContainerRequest();
        containerService.setSessionService(sessionService);
        String containerId = containerService.createContainer(request.getDataFiles());
        Assert.assertFalse(containerId.isBlank());
    }

    @Test
    public void successfulUploadContainer() throws IOException, URISyntaxException {
        String container = new String(Base64.getEncoder().encode(TestUtil.getFileInputStream(SIGNED_HASHCODE).readAllBytes()));
        String containerId = containerService.uploadContainer(container);
        Assert.assertFalse(containerId.isBlank());
    }

    @Test
    public void successfulGetContainer() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createSessionHolder());
        String container = containerService.getContainer(CONTAINER_ID);
        Assert.assertFalse(container.isBlank());
    }

    @Test
    public void successfulGetSignatures() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createSessionHolder());
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
