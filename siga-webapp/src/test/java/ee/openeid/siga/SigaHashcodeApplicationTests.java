package ee.openeid.siga;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.UUID;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ActiveProfiles({"test", "digidoc4jTest"})
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"siga.security.hmac.expiration=120", "siga.security.hmac.clock-skew=2"})
public class SigaHashcodeApplicationTests extends SigaBaseApplicationTests {

    @Test
    public void createDatafileContainerFailsWith404() throws Exception {
        postRequest(
                "/containers",
                createCreateContainerRequest(),
                status().isNotFound()
        );
    }

    @Test
    public void uploadDatafileContainerFailsWith404() throws Exception {
        String container = IOUtils.toString(getFileInputStream("datafile.asice"), Charset.defaultCharset());
        postRequest(
                "/upload/containers",
                createUploadContainerRequest(container, "datafile.asice"),
                status().isNotFound()
        );
    }

    @Test
    public void getDatafileContainerFailsWith404() throws Exception {
        getRequest(
                "/containers/" + UUID.randomUUID(),
                status().isNotFound()
        );
    }

    @Test
    public void startDatafileRemoteSigning() throws Exception {
        String signingCertificate = Base64.getEncoder().encodeToString(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded());
        postRequest(
                "/containers/" + UUID.randomUUID() + "/remotesigning",
                createStartRemoteSigningRequest(signingCertificate),
                status().isNotFound()
        );
    }

    @Test
    public void finalizeDatafileRemoteSigning() throws Exception {
        putRequest(
                "/containers/" + UUID.randomUUID() + "/remotesigning/" + UUID.randomUUID(),
                createFinalizeRemoteSigningRequest("signatureValue"),
                status().isNotFound()
        );
    }

}
