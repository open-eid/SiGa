package ee.openeid.siga.service.signature;

import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.session.HashCodeSessionService;
import ee.openeid.siga.webapp.json.CreateHashCodeContainerRequest;
import ee.openeid.siga.webapp.json.CreateHashCodeContainerResponse;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

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

}
