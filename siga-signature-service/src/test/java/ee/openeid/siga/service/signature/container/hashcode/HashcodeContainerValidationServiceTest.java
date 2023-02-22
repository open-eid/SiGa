package ee.openeid.siga.service.signature.container.hashcode;

import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.model.ServiceType;
import ee.openeid.siga.service.signature.client.SivaClient;
import ee.openeid.siga.service.signature.test.RequestUtil;
import ee.openeid.siga.service.signature.test.TestUtil;
import ee.openeid.siga.session.SessionService;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Base64;

import static ee.openeid.siga.service.signature.test.RequestUtil.SIGNED_HASHCODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class HashcodeContainerValidationServiceTest {

    @InjectMocks
    private HashcodeContainerValidationService validationService;

    private static final String HASHCODE_DDOC_FILE = "hashcode_container.ddoc";
    private static final String DDOC_FILE = "container.ddoc";

    @Mock
    private SivaClient sivaClient;
    @Mock
    private SessionService sessionService;

    @BeforeEach
    public void setUp() throws IOException, URISyntaxException {
        ValidationConclusion hashcodeValidationConclusion = RequestUtil.createValidationResponse().getValidationReport().getValidationConclusion();
        ValidationConclusion validationConclusion = RequestUtil.createValidationResponse().getValidationReport().getValidationConclusion();
        Mockito.lenient().when(sivaClient.validateContainer(any(), any())).thenReturn(validationConclusion);
        Mockito.lenient().when(sivaClient.validateHashcodeContainer(any(), any())).thenReturn(hashcodeValidationConclusion);
        Mockito.lenient().when(sessionService.getContainer(any())).thenReturn(RequestUtil.createHashcodeSessionHolder());
    }

    @Test
    public void DDOCHashcodeContainerValidation() throws IOException, URISyntaxException {
        ValidationConclusion validationConclusion = validationService
                .validateContainer(createContainer(HASHCODE_DDOC_FILE), ServiceType.REST);
        assertEquals(Integer.valueOf(1), validationConclusion.getValidSignaturesCount());
        assertEquals(Integer.valueOf(1), validationConclusion.getSignaturesCount());
    }

    @Test
    public void regularDDOCNotSupported() {
        InvalidContainerException caughtException = assertThrows(
                InvalidContainerException.class, () -> validationService.validateContainer(createContainer(DDOC_FILE), ServiceType.REST)
        );
        assertEquals("EMBEDDED DDOC is not supported", caughtException.getMessage());
    }

    @Test
    public void successfulHashcodeContainerValidation() throws IOException, URISyntaxException {
        ValidationConclusion validationConclusion = validationService
                .validateContainer(createContainer(SIGNED_HASHCODE), ServiceType.REST);
        assertEquals(Integer.valueOf(1), validationConclusion.getValidSignaturesCount());
        assertEquals(Integer.valueOf(1), validationConclusion.getSignaturesCount());
    }

    @Test
    public void successfulExistingContainerValidation() {
        ValidationConclusion validationConclusion = validationService.validateExistingContainer("12312312312");
        assertEquals(Integer.valueOf(1), validationConclusion.getValidSignaturesCount());
        assertEquals(Integer.valueOf(1), validationConclusion.getSignaturesCount());
    }

    private String createContainer(String fileName) throws IOException, URISyntaxException {
        InputStream inputStream = TestUtil.getFileInputStream(fileName);
        return new String(Base64.getEncoder().encode(IOUtils.toByteArray(inputStream)));
    }

}
