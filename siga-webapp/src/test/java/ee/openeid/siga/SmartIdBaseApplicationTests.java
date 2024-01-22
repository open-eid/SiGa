package ee.openeid.siga;

import ee.openeid.siga.common.model.CertificateStatus;
import ee.openeid.siga.service.signature.hashcode.HashcodeContainer;
import ee.openeid.siga.webapp.json.HashcodeDataFile;
import ee.openeid.siga.webapp.json.Signature;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class SmartIdBaseApplicationTests extends BaseTest {

    @Test
    public void smartIdHashcodeFlowWihCertificateChoice() throws Exception {
        String containerId = uploadHashcodeContainer();
        List<Signature> signatures = getHashcodeSignatures(containerId);
        assertEquals(1, signatures.size());
        HashcodeContainer originalContainer = getHashcodeContainer(containerId);
        assertEquals(1, originalContainer.getSignatures().size());
        assertEquals(2, originalContainer.getDataFiles().size());
        List<HashcodeDataFile> dataFiles = getHashcodeDataFiles(containerId);
        assertEquals(2, dataFiles.size());
        ValidationConclusion validationConclusion = getHashcodeValidationConclusion(containerId);
        assertEquals("JÃ\u0095EORG,JAAK-KRISTJAN,38001085718", validationConclusion.getSignatures().get(0).getSubjectDistinguishedName().getCommonName());
        assertEquals("PNOEE-38001085718", validationConclusion.getSignatures().get(0).getSubjectDistinguishedName().getSerialNumber());

        String certificateId = startHashcodeSmartIdCertificateChoice(containerId);
        AtomicReference<CertificateStatus> certificateStatusHolder = new AtomicReference<>();
        await().atMost(25, SECONDS).until(isHashcodeSmartIdCertificateChoiceSuccessful(certificateStatusHolder, containerId, certificateId));
        String signatureId = startHashcodeSmartIdSigning(containerId, certificateStatusHolder.get().getDocumentNumber());
        await().atMost(25, SECONDS).until(isHashcodeSmartIdResponseSuccessful(containerId, signatureId));

        assertHashcodeSignedContainer(containerId, 2);
        assertInfoIsLoggedOnce(".*event_type=FINISH, event_name=SMART_ID_GET_SIGN_HASH_STATUS, " +
                        "client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, service_name=test1.service.ee, " +
                        "service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, sid_status=OK, " +
                        "request_url=https://sid.demo.sk.ee/smart-id-rp/v2/,.* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=TSA_REQUEST, client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, " +
                        "service_name=test1.service.ee, service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, " +
                        "request_url=http://tsa.demo.sk.ee/tsa,.* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=OCSP_REQUEST, client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, " +
                        "service_name=test1.service.ee, service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, " +
                        "request_url=http://aia.demo.sk.ee/eid2016,.* result=SUCCESS.*");
    }

    @Test
    public void smartIdHashcodeSigningFlowWithDocumentNumber() throws Exception {
        String containerId = uploadHashcodeContainer();
        List<Signature> signatures = getHashcodeSignatures(containerId);

        assertEquals(1, signatures.size());
        HashcodeContainer originalContainer = getHashcodeContainer(containerId);
        assertEquals(1, originalContainer.getSignatures().size());
        assertEquals(2, originalContainer.getDataFiles().size());

        List<HashcodeDataFile> dataFiles = getHashcodeDataFiles(containerId);
        assertEquals(2, dataFiles.size());

        String signatureId = startHashcodeSmartIdSigning(containerId, null);
        await().atMost(25, SECONDS).until(isHashcodeSmartIdResponseSuccessful(containerId, signatureId));
        assertHashcodeSignedContainer(containerId, 2);
        assertInfoIsLoggedOnce(".*event_type=FINISH, event_name=SMART_ID_GET_SIGN_HASH_STATUS, " +
                        "client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, service_name=test1.service.ee, " +
                        "service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, sid_status=OK, " +
                        "request_url=https://sid.demo.sk.ee/smart-id-rp/v2/,.* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=TSA_REQUEST, client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, " +
                        "service_name=test1.service.ee, service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, " +
                        "request_url=http://tsa.demo.sk.ee/tsa,.* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=OCSP_REQUEST, client_name=client1, client_uuid=5f923dee-4e6f-4987-bce0-36ad9647ba58, " +
                        "service_name=test1.service.ee, service_uuid=a7fd7728-a3ea-4975-bfab-f240a67e894f, " +
                        "request_url=http://aia.demo.sk.ee/eid2016,.* result=SUCCESS.*");
    }

}
