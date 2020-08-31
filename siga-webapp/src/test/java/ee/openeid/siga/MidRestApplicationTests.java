package ee.openeid.siga;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.helper.TestBase;
import ee.openeid.siga.service.signature.hashcode.HashcodeContainer;
import ee.openeid.siga.webapp.json.DataFile;
import ee.openeid.siga.webapp.json.HashcodeDataFile;
import ee.openeid.siga.webapp.json.Signature;
import org.digidoc4j.Container;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static java.lang.String.valueOf;
import static java.time.Instant.now;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@ActiveProfiles({"test", "digidoc4jTest", "datafileContainer", "mobileId"})
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"siga.security.hmac.expiration=120", "siga.security.hmac.clock-skew=2"})
@AutoConfigureMockMvc
public class MidRestApplicationTests extends TestBase {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Before
    public void setup() {
        xAuthorizationTimestamp = valueOf(now().getEpochSecond());
    }

    @Override
    protected ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    @Override
    protected MockMvc getMockMvc() {
        return mockMvc;
    }

    @Test
    public void mobileIdSigningFlow() throws Exception {
        String containerId = uploadContainer();
        List<Signature> signatures = getSignatures(containerId);
        Assert.assertEquals(1, signatures.size());
        Container originalContainer = getContainer(containerId);
        Assert.assertEquals(1, originalContainer.getSignatures().size());
        Assert.assertEquals(2, originalContainer.getDataFiles().size());
        List<DataFile> dataFiles = getDataFiles(containerId);
        Assert.assertEquals(2, dataFiles.size());

        String signatureId = startMobileSigning(containerId);
        String mobileFirstStatus = getMobileIdStatus(containerId, signatureId);
        Assert.assertEquals("OUTSTANDING_TRANSACTION", mobileFirstStatus);
        await().atMost(15, SECONDS).until(isMobileIdResponseSuccessful(containerId, signatureId));

        assertSignedContainer(containerId, 2);
    }

    @Test
    public void mobileIdHashcodeSigningFlow() throws Exception {
        String containerId = uploadHashcodeContainer();
        List<Signature> signatures = getHashcodeSignatures(containerId);

        Assert.assertEquals(1, signatures.size());
        HashcodeContainer originalContainer = getHashcodeContainer(containerId);
        Assert.assertEquals(1, originalContainer.getSignatures().size());
        Assert.assertEquals(2, originalContainer.getDataFiles().size());

        List<HashcodeDataFile> dataFiles = getHashcodeDataFiles(containerId);
        Assert.assertEquals(2, dataFiles.size());
        Assert.assertEquals("RnKZobNWVy8u92sDL4S2j1BUzMT5qTgt6hm90TfAGRo=", dataFiles.get(0).getFileHashSha256());

        String signatureId = startHashcodeMobileSigning(containerId);
        String mobileFirstStatus = getHashcodeMobileIdStatus(containerId, signatureId);
        Assert.assertEquals("OUTSTANDING_TRANSACTION", mobileFirstStatus);
        await().atMost(15, SECONDS).until(isHashcodeMobileIdResponseSuccessful(containerId, signatureId));

        assertHashcodeSignedContainer(containerId, 2);
    }
}
