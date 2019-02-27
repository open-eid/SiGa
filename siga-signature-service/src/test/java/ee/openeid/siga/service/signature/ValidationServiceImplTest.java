package ee.openeid.siga.service.signature;

import ee.openeid.siga.service.signature.client.SivaClient;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.webapp.json.CreateHashCodeValidationReportRequest;
import ee.openeid.siga.webapp.json.CreateHashCodeValidationReportResponse;
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

import static org.mockito.ArgumentMatchers.any;

@RunWith(MockitoJUnitRunner.class)
public class ValidationServiceImplTest {

    private static final String SIGNED_HASHCODE = "hashcode.asice";

    @InjectMocks
    private ValidationServiceImpl validationService;

    @Mock
    private SivaClient sivaClient;

    @Before
    public void setUp() {
        ValidationConclusion validationConclusion = RequestUtil.createValidationResponse().getValidationReport().getValidationConclusion();
        Mockito.when(sivaClient.validateHashCodeContainer(any(), any())).thenReturn(validationConclusion);
    }

    @Test
    public void successfulRequest() throws IOException, URISyntaxException {
        CreateHashCodeValidationReportResponse response = validationService.validateContainer(createRequest());
        Assert.assertEquals(Integer.valueOf(1), response.getValidationConclusion().getValidSignaturesCount());
        Assert.assertEquals(Integer.valueOf(1), response.getValidationConclusion().getSignaturesCount());
    }

    private CreateHashCodeValidationReportRequest createRequest() throws IOException, URISyntaxException {
        InputStream inputStream = TestUtil.getFileInputStream(SIGNED_HASHCODE);
        CreateHashCodeValidationReportRequest request = new CreateHashCodeValidationReportRequest();
        request.setContainer(new String(Base64.getEncoder().encode(IOUtils.toByteArray(inputStream))));
        request.setContainerName("asice.asice");
        return request;
    }

}
