package ee.openeid.siga;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.common.model.ServiceType;
import ee.openeid.siga.service.signature.hashcode.HashcodeContainer;
import ee.openeid.siga.webapp.json.HashcodeDataFile;
import ee.openeid.siga.webapp.json.Signature;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static java.lang.String.valueOf;
import static java.time.Instant.now;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@ExtendWith(SpringExtension.class)
@ActiveProfiles({"test", "digidoc4jTest", "datafileContainer", "mobileId"})
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"siga.security.hmac.expiration=120", "siga.security.hmac.clock-skew=2"})
@AutoConfigureMockMvc
public class SigaProxyApplicationTests extends BaseTest {

    private final static String HMAC_SHARED_SECRET = "746573745365637265744b6579303037";
    private final static String REQUESTING_SERVICE_UUID = "7dc75cb8-7076-4bed-9f06-b304f85cdccd";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        xAuthorizationTimestamp = valueOf(now().getEpochSecond());
    }

    @Test
    public void sha256SupportedCreateContainerFlow() throws Exception {
        String containerId = createHashcodeContainerWithSha256();
        String signatureId = startHashcodeMobileSigning(containerId);
        String mobileFirstStatus = getHashcodeMobileIdStatus(containerId, signatureId);
        assertEquals("OUTSTANDING_TRANSACTION", mobileFirstStatus);
        await().atMost(16, SECONDS).until(isHashcodeMobileIdResponseSuccessful(containerId, signatureId));
        HashcodeContainer container = getHashcodeContainer(containerId);
        assertEquals(1, container.getSignatures().size());
        assertEquals(1, container.getDataFiles().size());

        List<Signature> signatures = getHashcodeSignatures(containerId);

        assertEquals(1, signatures.size());
        ValidationConclusion validationConclusion = getHashcodeValidationConclusion(containerId);
        assertEquals(Integer.valueOf(1), validationConclusion.getValidSignaturesCount());
        assertEquals(Integer.valueOf(1), validationConclusion.getSignaturesCount());
    }

    @Test
    public void sha256SupportedUploadContainerFlow() throws Exception {
        String containerId = uploadHashcodeContainer("hashcodeMissingSha512File.asice");
        List<Signature> signatures = getHashcodeSignatures(containerId);

        assertEquals(1, signatures.size());
        HashcodeContainer originalContainer = getHashcodeContainer(containerId);
        assertEquals(1, originalContainer.getSignatures().size());
        assertEquals(2, originalContainer.getDataFiles().size());

        List<HashcodeDataFile> dataFiles = getHashcodeDataFiles(containerId);
        assertEquals(2, dataFiles.size());

        String signatureId = startHashcodeMobileSigning(containerId);
        String mobileFirstStatus = getHashcodeMobileIdStatus(containerId, signatureId);
        assertEquals("OUTSTANDING_TRANSACTION", mobileFirstStatus);
        await().atMost(16, SECONDS).until(isHashcodeMobileIdResponseSuccessful(containerId, signatureId));
        assertHashcodeSignedContainer(containerId, 2);
    }

    @Test
    public void sha256SupportedValidationReport() throws Exception {
        ValidationConclusion validationConclusion = getValidationConclusionByUploadingContainer("hashcodeMissingSha512File.asice");
        assertEquals(Integer.valueOf(1), validationConclusion.getValidSignaturesCount());
        assertEquals(Integer.valueOf(1), validationConclusion.getSignaturesCount());
    }

    @Override
    protected String getServiceUuid() {
        return REQUESTING_SERVICE_UUID;
    }

    @Override
    protected String getHmacSharedSecret() {
        return HMAC_SHARED_SECRET;
    }

    @Override
    protected ServiceType getServiceType() {
        return ServiceType.PROXY;
    }

    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    protected MockMvc getMockMvc() {
        return mockMvc;
    }
}
