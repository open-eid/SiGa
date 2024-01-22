package ee.openeid.siga;

import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
import ee.openeid.siga.webapp.json.GetContainerSignatureDetailsResponse;
import ee.openeid.siga.webapp.json.Signature;
import org.apache.commons.codec.binary.Hex;
import org.digidoc4j.Container;
import org.digidoc4j.DigestAlgorithm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@ExtendWith(SpringExtension.class)
@ActiveProfiles({"test", "digidoc4jTest", "datafileContainer"})
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"siga.security.hmac.expiration=120", "siga.security.hmac.clock-skew=2"})
class SigaDatafileApplicationTests extends SigaBaseApplicationTests {

    @Test
    void dataFileModifyingContainerFlow() throws Exception {
        String containerId = createContainer();
        Container originalContainer = getContainer(containerId);
        assertEquals(0, originalContainer.getSignatures().size());
        assertEquals(1, originalContainer.getDataFiles().size());
        addDataFile(containerId);
        Container updatedContainer = getContainer(containerId);
        assertEquals("random text", new String(updatedContainer.getDataFiles().get(0).getBytes()));
        assertEquals("random text", new String(updatedContainer.getDataFiles().get(1).getBytes()));

        assertEquals(2, updatedContainer.getDataFiles().size());
        deleteDataFile(containerId, updatedContainer.getDataFiles().get(0).getName());
        Container updatedContainer2 = getContainer(containerId);
        assertEquals(1, updatedContainer2.getDataFiles().size());
    }

    @Test
    void remoteDatafileSigningFlowWithBase64EncodedCertificate() throws Exception {
        String containerId = uploadContainer();
        List<Signature> signatures = getSignatures(containerId);
        GetContainerSignatureDetailsResponse signatureResponse = getSignature(containerId, signatures.get(0).getGeneratedSignatureId());
        assertEquals("S0", signatureResponse.getId());
        assertEquals(1, signatures.size());
        Container originalContainer = getContainer(containerId);
        assertEquals(1, originalContainer.getSignatures().size());
        assertEquals(2, originalContainer.getDataFiles().size());
        String signingCertificate = Base64.getEncoder().encodeToString(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded());

        CreateContainerRemoteSigningResponse startRemoteSigningResponse = startRemoteSigning(containerId, signingCertificate);
        byte[] dataToSign = Base64.getDecoder().decode(startRemoteSigningResponse.getDataToSign());
        byte[] signedData = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.findByAlgorithm(startRemoteSigningResponse.getDigestAlgorithm()), dataToSign);
        String signatureValue = new String(Base64.getEncoder().encode(signedData));
        finalizeRemoteSigning("/containers/" + containerId + "/remotesigning/" + startRemoteSigningResponse.getGeneratedSignatureId(), signatureValue);

        assertSignedContainer(containerId, 2);
        assertInfoIsLoggedOnce(".*event_type=FINISH, event_name=TSA_REQUEST, .* request_url=http://tsa.demo.sk.ee/tsa, .* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=OCSP_REQUEST, .* request_url=http://aia.demo.sk.ee/esteid2018, .* result=SUCCESS.*");
    }

    @Test
    void remoteDatafileSigningFlowWithHexEncodedCertificate() throws Exception {
        String containerId = uploadContainer();
        List<Signature> signatures = getSignatures(containerId);
        GetContainerSignatureDetailsResponse signatureResponse = getSignature(containerId, signatures.get(0).getGeneratedSignatureId());
        assertEquals("S0", signatureResponse.getId());
        assertEquals(1, signatures.size());
        Container originalContainer = getContainer(containerId);
        assertEquals(1, originalContainer.getSignatures().size());
        assertEquals(2, originalContainer.getDataFiles().size());
        String signingCertificate = Hex.encodeHexString(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded());

        CreateContainerRemoteSigningResponse startRemoteSigningResponse = startRemoteSigning(containerId, signingCertificate);
        byte[] dataToSign = Base64.getDecoder().decode(startRemoteSigningResponse.getDataToSign());
        byte[] signedData = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.findByAlgorithm(startRemoteSigningResponse.getDigestAlgorithm()), dataToSign);
        String signatureValue = new String(Base64.getEncoder().encode(signedData));
        finalizeRemoteSigning("/containers/" + containerId + "/remotesigning/" + startRemoteSigningResponse.getGeneratedSignatureId(), signatureValue);

        assertSignedContainer(containerId, 2);
        assertInfoIsLoggedOnce(".*event_type=FINISH, event_name=TSA_REQUEST, .* request_url=http://tsa.demo.sk.ee/tsa, .* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=OCSP_REQUEST, .* request_url=http://aia.demo.sk.ee/esteid2018, .* result=SUCCESS.*");
    }

}
