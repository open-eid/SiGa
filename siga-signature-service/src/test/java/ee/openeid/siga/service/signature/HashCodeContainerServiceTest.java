package ee.openeid.siga.service.signature;

import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.session.HashCodeSessionService;
import ee.openeid.siga.webapp.json.*;
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

import static ee.openeid.siga.service.signature.test.RequestUtil.CONTAINER_NAME;
import static ee.openeid.siga.service.signature.test.RequestUtil.SIGNED_HASHCODE;
import static org.mockito.ArgumentMatchers.any;

@RunWith(MockitoJUnitRunner.class)
public class HashCodeContainerServiceTest {

    public static final String CONTAINER_ID = "23423423-234234234-324234-4234";

    @InjectMocks
    HashCodeContainerService containerService;

    @Mock
    private HashCodeSessionService sessionService;

    @Test
    public void successfulCreateContainer() {
        CreateHashCodeContainerRequest request = RequestUtil.getHashCodeCreateContainerRequest();
        containerService.setSessionService(sessionService);
        CreateHashCodeContainerResponse response = containerService.createContainer(request);
        Assert.assertFalse(response.getContainerId().isBlank());
    }

    @Test
    public void successfulUploadContainer() throws IOException, URISyntaxException {
        String container = new String(Base64.getEncoder().encode(TestUtil.getFileInputStream(SIGNED_HASHCODE).readAllBytes()));
        UploadHashCodeContainerRequest request = new UploadHashCodeContainerRequest();
        request.setContainer(container);
        request.setContainerName(CONTAINER_NAME);
        UploadHashCodeContainerResponse response = containerService.uploadContainer(request);
        Assert.assertFalse(response.getContainerId().isBlank());
    }

    @Test
    public void successfulGetContainer() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createSessionHolder());
        GetHashCodeContainerResponse container = containerService.getContainer(CONTAINER_ID);
        Assert.assertFalse(container.getContainer().isBlank());
        Assert.assertEquals(CONTAINER_NAME, container.getContainerName());
    }

    @Test
    public void successfulGetSignatures() throws IOException, URISyntaxException {
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createSessionHolder());
        GetHashCodeSignaturesResponse response = containerService.getSignatures(CONTAINER_ID);
        Assert.assertEquals("id-a9fae00496ae203a6a8b92adbe762bd3", response.getSignatures().get(0).getId());
        Assert.assertEquals("LT", response.getSignatures().get(0).getSignatureProfile());
        Assert.assertEquals("SERIALNUMBER=PNOEE-38001085718, GIVENNAME=JAAK-KRISTJAN, SURNAME=JÕEORG, CN=\"JÕEORG,JAAK-KRISTJAN,38001085718\", C=EE", response.getSignatures().get(0).getSignerInfo());
    }

}
