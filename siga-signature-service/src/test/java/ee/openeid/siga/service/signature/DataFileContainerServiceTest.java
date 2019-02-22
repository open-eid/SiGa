package ee.openeid.siga.service.signature;

import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.session.DataFileSessionService;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.CreateContainerResponse;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DataFileContainerServiceTest {


    @InjectMocks
    DataFileContainerService containerService;

    @Mock
    private DataFileSessionService sessionService;


    @Test
    public void successfulCreateContainer() {
        CreateContainerRequest request = RequestUtil.getCreateContainerRequest();
        containerService.setSessionService(sessionService);
        CreateContainerResponse response = containerService.createContainer(request);
        Assert.assertFalse(response.getSessionId().isBlank());
    }
}
