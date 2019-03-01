package ee.openeid.siga.service.signature;

import ee.openeid.siga.service.signature.client.SivaClient;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.webapp.json.CreateHashCodeValidationReportRequest;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Base64;

import static ee.openeid.siga.service.signature.test.RequestUtil.SIGNED_HASHCODE;
import static org.mockito.ArgumentMatchers.any;

@RunWith(MockitoJUnitRunner.class)
public class DetachedDataFileContainerValidationServiceTest {

    @InjectMocks
    private DetachedDataFileContainerValidationService validationService;

    @Mock
    private SivaClient sivaClient;
    @Mock
    private SessionService sessionService;


    @Before
    public void setUp() throws IOException, URISyntaxException {
        ValidationConclusion validationConclusion = RequestUtil.createValidationResponse().getValidationReport().getValidationConclusion();
        Mockito.when(sivaClient.validateDetachedDataFileContainer(any(), any())).thenReturn(validationConclusion);
        Mockito.when(sessionService.getContainer(any())).thenReturn(RequestUtil.createSessionHolder());
    }

    @Test
    public void successfulContainerValidation() throws IOException, URISyntaxException {
        ValidationConclusion validationConclusion = validationService.validateContainer(createRequest().getContainer());
        Assert.assertEquals(Integer.valueOf(1), validationConclusion.getValidSignaturesCount());
        Assert.assertEquals(Integer.valueOf(1), validationConclusion.getSignaturesCount());
    }

    @Test
    public void successfulExistingContainerValidation() {
        validationService.setSessionService(sessionService);
        ValidationConclusion validationConclusion = validationService.validateExistingContainer("12312312312");
        Assert.assertEquals(Integer.valueOf(1), validationConclusion.getValidSignaturesCount());
        Assert.assertEquals(Integer.valueOf(1), validationConclusion.getSignaturesCount());
    }

    private CreateHashCodeValidationReportRequest createRequest() throws IOException, URISyntaxException {
        InputStream inputStream = TestUtil.getFileInputStream(SIGNED_HASHCODE);
        CreateHashCodeValidationReportRequest request = new CreateHashCodeValidationReportRequest();
        request.setContainer(new String(Base64.getEncoder().encode(IOUtils.toByteArray(inputStream))));
        return request;
    }

}
