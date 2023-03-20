package ee.openeid.siga;

import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
import ee.openeid.siga.webapp.json.GetContainerSignatureDetailsResponse;
import ee.openeid.siga.webapp.json.Signature;
import org.apache.commons.codec.binary.Hex;
import org.digidoc4j.Container;
import org.digidoc4j.DigestAlgorithm;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Base64;
import java.util.List;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@ActiveProfiles({"test", "digidoc4jTest", "datafileContainer"})
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"siga.security.hmac.expiration=120", "siga.security.hmac.clock-skew=2"})
public class SigaDatafileApplicationTests extends SigaBaseApplicationTests {

    @Test
    public void dataFileModifyingContainerFlow() throws Exception {
        String containerId = createContainer();
        Container originalContainer = getContainer(containerId);
        Assert.assertEquals(0, originalContainer.getSignatures().size());
        Assert.assertEquals(1, originalContainer.getDataFiles().size());
        addDataFile(containerId);
        Container updatedContainer = getContainer(containerId);
        Assert.assertEquals("random text", new String(updatedContainer.getDataFiles().get(0).getBytes()));
        Assert.assertEquals("random text", new String(updatedContainer.getDataFiles().get(1).getBytes()));

        Assert.assertEquals(2, updatedContainer.getDataFiles().size());
        deleteDataFile(containerId, updatedContainer.getDataFiles().get(0).getName());
        Container updatedContainer2 = getContainer(containerId);
        Assert.assertEquals(1, updatedContainer2.getDataFiles().size());
    }

    @Test
    public void remoteDatafileSigningFlowWithBase64EncodedCertificate() throws Exception {
        String containerId = uploadContainer();
        List<Signature> signatures = getSignatures(containerId);
        GetContainerSignatureDetailsResponse signatureResponse = getSignature(containerId, signatures.get(0).getGeneratedSignatureId());
        Assert.assertEquals("S0", signatureResponse.getId());
        Assert.assertEquals(1, signatures.size());
        Container originalContainer = getContainer(containerId);
        Assert.assertEquals(1, originalContainer.getSignatures().size());
        Assert.assertEquals(2, originalContainer.getDataFiles().size());
        String signingCertificate = Base64.getEncoder().encodeToString(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded());

        CreateContainerRemoteSigningResponse startRemoteSigningResponse = startRemoteSigning(containerId, signingCertificate);
        byte[] dataToSign = Base64.getDecoder().decode(startRemoteSigningResponse.getDataToSign());
        byte[] signedData = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.findByAlgorithm(startRemoteSigningResponse.getDigestAlgorithm()), dataToSign);
        String signatureValue = new String(Base64.getEncoder().encode(signedData));
        finalizeRemoteSigning("/containers/" + containerId + "/remotesigning/" + startRemoteSigningResponse.getGeneratedSignatureId(), signatureValue);

        assertSignedContainer(containerId, 2);
        assertInfoIsLoggedOnce(".*event_type=FINISH, event_name=TSA_REQUEST, .* request_url=http://demo.sk.ee/tsa, .* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=OCSP_REQUEST, .* request_url=http://aia.demo.sk.ee/esteid2018, .* result=SUCCESS.*");
    }

    @Test
    public void remoteDatafileSigningFlowWithHexEncodedCertificate() throws Exception {
        String containerId = uploadContainer();
        List<Signature> signatures = getSignatures(containerId);
        GetContainerSignatureDetailsResponse signatureResponse = getSignature(containerId, signatures.get(0).getGeneratedSignatureId());
        Assert.assertEquals("S0", signatureResponse.getId());
        Assert.assertEquals(1, signatures.size());
        Container originalContainer = getContainer(containerId);
        Assert.assertEquals(1, originalContainer.getSignatures().size());
        Assert.assertEquals(2, originalContainer.getDataFiles().size());
        String signingCertificate = Hex.encodeHexString(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded());

        CreateContainerRemoteSigningResponse startRemoteSigningResponse = startRemoteSigning(containerId, signingCertificate);
        byte[] dataToSign = Base64.getDecoder().decode(startRemoteSigningResponse.getDataToSign());
        byte[] signedData = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.findByAlgorithm(startRemoteSigningResponse.getDigestAlgorithm()), dataToSign);
        String signatureValue = new String(Base64.getEncoder().encode(signedData));
        finalizeRemoteSigning("/containers/" + containerId + "/remotesigning/" + startRemoteSigningResponse.getGeneratedSignatureId(), signatureValue);

        assertSignedContainer(containerId, 2);
        assertInfoIsLoggedOnce(".*event_type=FINISH, event_name=TSA_REQUEST, .* request_url=http://demo.sk.ee/tsa, .* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=OCSP_REQUEST, .* request_url=http://aia.demo.sk.ee/esteid2018, .* result=SUCCESS.*");
    }

}
