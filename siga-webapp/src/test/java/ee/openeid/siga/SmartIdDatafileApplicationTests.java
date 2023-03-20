package ee.openeid.siga;

import ee.openeid.siga.common.model.CertificateStatus;
import ee.openeid.siga.webapp.json.DataFile;
import ee.openeid.siga.webapp.json.Signature;
import org.digidoc4j.Container;
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
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@ExtendWith(SpringExtension.class)
@ActiveProfiles({"test", "digidoc4jTest", "datafileContainer", "smartId"})
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"siga.security.hmac.expiration=120", "siga.security.hmac.clock-skew=2"})
public class SmartIdDatafileApplicationTests extends SmartIdBaseApplicationTests {

    @Test
    public void smartIdDatafileFlowWihCertificateChoice() throws Exception {
        String containerId = uploadContainer();
        List<Signature> signatures = getSignatures(containerId);

        assertEquals(1, signatures.size());
        Container originalContainer = getContainer(containerId);
        assertEquals(1, originalContainer.getSignatures().size());
        assertEquals(2, originalContainer.getDataFiles().size());

        List<DataFile> dataFiles = getDataFiles(containerId);
        assertEquals(2, dataFiles.size());
        String certificateId = startSmartIdCertificateChoice(containerId);
        AtomicReference<CertificateStatus> certificateStatusHolder = new AtomicReference<>();
        await().atMost(25, SECONDS).until(isSmartIdCertificateChoiceSuccessful(certificateStatusHolder, containerId, certificateId));
        String signatureId = startSmartIdSigning(containerId, certificateStatusHolder.get().getDocumentNumber());
        await().atMost(25, SECONDS).until(isSmartIdResponseSuccessful(containerId, signatureId));
        assertSignedContainer(containerId, 2);
        assertInfoIsLoggedOnce(".*event_type=FINISH, event_name=SMART_ID_GET_SIGN_HASH_STATUS, " +
                        "client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, service_name=test1.service.ee, " +
                        "service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, sid_status=OK, " +
                        "request_url=https://sid.demo.sk.ee/smart-id-rp/v2/,.* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=TSA_REQUEST, client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, " +
                        "service_name=test1.service.ee, service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, " +
                        "request_url=http://demo.sk.ee/tsa,.* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=OCSP_REQUEST, client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, " +
                        "service_name=test1.service.ee, service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, " +
                        "request_url=http://aia.demo.sk.ee/eid2016,.* result=SUCCESS.*");
    }

    @Test
    public void smartIdDatafileSigningFlowWithDocumentNumber() throws Exception {
        String containerId = uploadContainer();
        List<Signature> signatures = getSignatures(containerId);

        assertEquals(1, signatures.size());
        Container originalContainer = getContainer(containerId);
        assertEquals(1, originalContainer.getSignatures().size());
        assertEquals(2, originalContainer.getDataFiles().size());

        List<DataFile> dataFiles = getDataFiles(containerId);
        assertEquals(2, dataFiles.size());

        String signatureId = startSmartIdSigning(containerId, null);
        await().atMost(25, SECONDS).until(isSmartIdResponseSuccessful(containerId, signatureId));
        assertSignedContainer(containerId, 2);
        assertInfoIsLoggedOnce(".*event_type=FINISH, event_name=SMART_ID_GET_SIGN_HASH_STATUS, " +
                        "client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, service_name=test1.service.ee, " +
                        "service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, sid_status=OK, " +
                        "request_url=https://sid.demo.sk.ee/smart-id-rp/v2/,.* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=TSA_REQUEST, client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, " +
                        "service_name=test1.service.ee, service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, " +
                        "request_url=http://demo.sk.ee/tsa,.* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=OCSP_REQUEST, client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, " +
                        "service_name=test1.service.ee, service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, " +
                        "request_url=http://aia.demo.sk.ee/eid2016,.* result=SUCCESS.*");
    }

}
