package ee.openeid.siga;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.auth.filter.hmac.HmacSignature;
import ee.openeid.siga.service.signature.hashcode.HashcodeContainer;
import ee.openeid.siga.helper.TestBase;
import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import ee.openeid.siga.webapp.json.DataFile;
import ee.openeid.siga.webapp.json.GetContainerSignatureDetailsResponse;
import ee.openeid.siga.webapp.json.GetHashcodeContainerSignatureDetailsResponse;
import ee.openeid.siga.webapp.json.HashcodeDataFile;
import ee.openeid.siga.webapp.json.Signature;
import org.apache.commons.codec.binary.Hex;
import org.digidoc4j.Container;
import org.digidoc4j.DigestAlgorithm;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Base64;
import java.util.List;

import static java.lang.String.valueOf;
import static java.time.Instant.now;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ActiveProfiles({"test", "digidoc4jTest"})
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"siga.security.hmac.expiration=120", "siga.security.hmac.clock-skew=2"})
@AutoConfigureMockMvc
public class SigaApplicationTests extends TestBase {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Before
    public void setup() {
        xAuthorizationTimestamp = valueOf(now().getEpochSecond());
    }

    @Test
    public void hashcodeModifyingContainerFlow() throws Exception {
        String containerId = createHashcodeContainer();
        HashcodeContainer originalContainer = getHashcodeContainer(containerId);
        Assert.assertEquals(0, originalContainer.getSignatures().size());
        Assert.assertEquals(1, originalContainer.getDataFiles().size());
        addHashcodeDataFile(containerId);
        HashcodeContainer updatedContainer = getHashcodeContainer(containerId);
        Assert.assertEquals(0, updatedContainer.getSignatures().size());
        Assert.assertEquals(2, updatedContainer.getDataFiles().size());
        deleteHashcodeDataFile(containerId, updatedContainer.getDataFiles().get(0).getFileName());
        HashcodeContainer updatedContainer2 = getHashcodeContainer(containerId);
        Assert.assertEquals(0, updatedContainer2.getSignatures().size());
        Assert.assertEquals(1, updatedContainer2.getDataFiles().size());
    }

    @Test
    @Ignore("DD4J-461")
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
    public void getAnotherUserContainer() throws Exception {

        String containerId = uploadHashcodeContainer();
        HashcodeContainer originalContainer = getHashcodeContainer(containerId);
        Assert.assertEquals(1, originalContainer.getSignatures().size());
        Assert.assertEquals(2, originalContainer.getDataFiles().size());

        String uuid = "824dcfe9-5c26-4d76-829a-e6630f434746";
        String sharedSecret = "746573745365637265744b6579303032";
        JSONObject request = new JSONObject();
        String signature = HmacSignature.builder()
                .macAlgorithm(DEFAULT_HMAC_ALGO)
                .serviceUuid(uuid)
                .timestamp(xAuthorizationTimestamp)
                .requestMethod("GET")
                .uri("/hashcodecontainers/" + containerId)
                .payload(request.toString().getBytes())
                .build().getSignature(sharedSecret);

        MockHttpServletRequestBuilder builder = get("/hashcodecontainers/" + containerId);
        mockMvc.perform(buildRequest(builder, signature, request, uuid))
                .andExpect(status().is(400));
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
        Thread.sleep(8000);
        String mobileStatus = getMobileIdStatus(containerId, signatureId);
        Assert.assertEquals("SIGNATURE", mobileStatus);
        assertSignedContainer(containerId, 2);
    }

    @Test
    public void remoteSigningFlowWithBase64EncodedCertificate() throws Exception {
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
    }

    @Test
    public void remoteSigningFlowWithHexEncodedCertificate() throws Exception {
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

        String signatureId = startHashcodeMobileSigning(containerId);
        String mobileFirstStatus = getHashcodeMobileIdStatus(containerId, signatureId);
        Assert.assertEquals("OUTSTANDING_TRANSACTION", mobileFirstStatus);
        Thread.sleep(8000);
        String mobileStatus = getHashcodeMobileIdStatus(containerId, signatureId);
        Assert.assertEquals("SIGNATURE", mobileStatus);
        assertHashcodeSignedContainer(containerId, 2);
    }

    @Test
    public void remoteHashcodeSigningFlowWithBase64EncodedCertificate() throws Exception {
        String containerId = uploadHashcodeContainer();
        List<Signature> signatures = getHashcodeSignatures(containerId);
        GetHashcodeContainerSignatureDetailsResponse signatureResponse = getHashcodeSignature(containerId, signatures.get(0).getGeneratedSignatureId());
        Assert.assertEquals("id-a9fae00496ae203a6a8b92adbe762bd3", signatureResponse.getId());

        Assert.assertEquals(1, signatures.size());
        HashcodeContainer originalContainer = getHashcodeContainer(containerId);
        Assert.assertEquals(1, originalContainer.getSignatures().size());
        Assert.assertEquals(2, originalContainer.getDataFiles().size());
        String signingCertificate = Base64.getEncoder().encodeToString(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded());
        CreateHashcodeContainerRemoteSigningResponse startRemoteSigningResponse = startHashcodeRemoteSigning(containerId, signingCertificate);
        byte[] dataToSign = Base64.getDecoder().decode(startRemoteSigningResponse.getDataToSign());
        byte[] signedData = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.findByAlgorithm(startRemoteSigningResponse.getDigestAlgorithm()), dataToSign);
        String signatureValue = new String(Base64.getEncoder().encode(signedData));
        finalizeRemoteSigning("/hashcodecontainers/" + containerId + "/remotesigning/" + startRemoteSigningResponse.getGeneratedSignatureId(), signatureValue);
        assertHashcodeSignedContainer(containerId, 2);
    }

    @Test
    public void remoteHashcodeSigningFlowWithHexEncodedCertificate() throws Exception {
        String containerId = uploadHashcodeContainer();
        List<Signature> signatures = getHashcodeSignatures(containerId);
        GetHashcodeContainerSignatureDetailsResponse signatureResponse = getHashcodeSignature(containerId, signatures.get(0).getGeneratedSignatureId());
        Assert.assertEquals("id-a9fae00496ae203a6a8b92adbe762bd3", signatureResponse.getId());

        Assert.assertEquals(1, signatures.size());
        HashcodeContainer originalContainer = getHashcodeContainer(containerId);
        Assert.assertEquals(1, originalContainer.getSignatures().size());
        Assert.assertEquals(2, originalContainer.getDataFiles().size());
        String signingCertificate = Hex.encodeHexString(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded());
        CreateHashcodeContainerRemoteSigningResponse startRemoteSigningResponse = startHashcodeRemoteSigning(containerId, signingCertificate);
        byte[] dataToSign = Base64.getDecoder().decode(startRemoteSigningResponse.getDataToSign());
        byte[] signedData = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.findByAlgorithm(startRemoteSigningResponse.getDigestAlgorithm()), dataToSign);
        String signatureValue = new String(Base64.getEncoder().encode(signedData));
        finalizeRemoteSigning("/hashcodecontainers/" + containerId + "/remotesigning/" + startRemoteSigningResponse.getGeneratedSignatureId(), signatureValue);
        assertHashcodeSignedContainer(containerId, 2);
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

