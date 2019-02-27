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
        GetHashCodeContainerResponse container = containerService.getContainer("23423423-234234234-324234-4234");
        Assert.assertFalse(container.getContainer().isBlank());
        Assert.assertEquals(CONTAINER_NAME, container.getContainerName());
    }

}
