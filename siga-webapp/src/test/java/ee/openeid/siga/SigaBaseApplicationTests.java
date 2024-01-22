package ee.openeid.siga;

import ee.openeid.siga.auth.filter.hmac.HmacSignature;
import ee.openeid.siga.service.signature.hashcode.HashcodeContainer;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import ee.openeid.siga.webapp.json.GetContainerSignatureDetailsResponse;
import ee.openeid.siga.webapp.json.Signature;
import org.apache.commons.codec.binary.Hex;
import org.digidoc4j.DigestAlgorithm;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Base64;
import java.util.List;

import static java.lang.String.valueOf;
import static java.time.Instant.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class SigaBaseApplicationTests extends BaseTest {

    @Test
    public void hashcodeModifyingContainerFlow() throws Exception {
        String containerId = createHashcodeContainerWithBothHashes();
        HashcodeContainer originalContainer = getHashcodeContainer(containerId);
        assertEquals(0, originalContainer.getSignatures().size());
        assertEquals(1, originalContainer.getDataFiles().size());
        addHashcodeDataFile(containerId);
        HashcodeContainer updatedContainer = getHashcodeContainer(containerId);
        assertEquals(0, updatedContainer.getSignatures().size());
        assertEquals(2, updatedContainer.getDataFiles().size());
        deleteHashcodeDataFile(containerId, updatedContainer.getDataFiles().get(0).getFileName());
        HashcodeContainer updatedContainer2 = getHashcodeContainer(containerId);
        assertEquals(0, updatedContainer2.getSignatures().size());
        assertEquals(1, updatedContainer2.getDataFiles().size());
    }

    @Test
    public void hashcodeGetAnotherUserContainer() throws Exception {
        String containerId = uploadHashcodeContainer();
        HashcodeContainer originalContainer = getHashcodeContainer(containerId);
        assertEquals(1, originalContainer.getSignatures().size());
        assertEquals(2, originalContainer.getDataFiles().size());
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
    public void remoteHashcodeSigningFlowWithBase64EncodedCertificate() throws Exception {
        String containerId = uploadHashcodeContainer();
        List<Signature> signatures = getHashcodeSignatures(containerId);
        GetContainerSignatureDetailsResponse signatureResponse = getHashcodeSignature(containerId, signatures.get(0).getGeneratedSignatureId());
        assertEquals("id-a9fae00496ae203a6a8b92adbe762bd3", signatureResponse.getId());
        assertEquals(1, signatures.size());
        HashcodeContainer originalContainer = getHashcodeContainer(containerId);
        assertEquals(1, originalContainer.getSignatures().size());
        assertEquals(2, originalContainer.getDataFiles().size());
        String signingCertificate = Base64.getEncoder().encodeToString(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded());

        CreateHashcodeContainerRemoteSigningResponse startRemoteSigningResponse = startHashcodeRemoteSigning(containerId, signingCertificate);
        byte[] dataToSign = Base64.getDecoder().decode(startRemoteSigningResponse.getDataToSign());
        byte[] signedData = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.findByAlgorithm(startRemoteSigningResponse.getDigestAlgorithm()), dataToSign);
        String signatureValue = new String(Base64.getEncoder().encode(signedData));
        finalizeRemoteSigning("/hashcodecontainers/" + containerId + "/remotesigning/" + startRemoteSigningResponse.getGeneratedSignatureId(), signatureValue);

        assertHashcodeSignedContainer(containerId, 2);
        assertInfoIsLoggedOnce(".*event_type=FINISH, event_name=TSA_REQUEST, .* request_url=http://tsa.demo.sk.ee/tsa, .* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=OCSP_REQUEST, .* request_url=http://aia.demo.sk.ee/esteid2018, .* result=SUCCESS.*");
    }

    @Test
    public void remoteHashcodeSigningFlowWithHexEncodedCertificate() throws Exception {
        String containerId = uploadHashcodeContainer();
        List<Signature> signatures = getHashcodeSignatures(containerId);
        GetContainerSignatureDetailsResponse signatureResponse = getHashcodeSignature(containerId, signatures.get(0).getGeneratedSignatureId());
        assertEquals("id-a9fae00496ae203a6a8b92adbe762bd3", signatureResponse.getId());
        assertEquals(1, signatures.size());
        HashcodeContainer originalContainer = getHashcodeContainer(containerId);
        assertEquals(1, originalContainer.getSignatures().size());
        assertEquals(2, originalContainer.getDataFiles().size());
        String signingCertificate = Hex.encodeHexString(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded());

        CreateHashcodeContainerRemoteSigningResponse startRemoteSigningResponse = startHashcodeRemoteSigning(containerId, signingCertificate);
        byte[] dataToSign = Base64.getDecoder().decode(startRemoteSigningResponse.getDataToSign());
        byte[] signedData = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.findByAlgorithm(startRemoteSigningResponse.getDigestAlgorithm()), dataToSign);
        String signatureValue = new String(Base64.getEncoder().encode(signedData));
        finalizeRemoteSigning("/hashcodecontainers/" + containerId + "/remotesigning/" + startRemoteSigningResponse.getGeneratedSignatureId(), signatureValue);

        assertHashcodeSignedContainer(containerId, 2);
        assertInfoIsLoggedOnce(".*event_type=FINISH, event_name=TSA_REQUEST, .* request_url=http://tsa.demo.sk.ee/tsa, .* result=SUCCESS.*",
                ".*event_type=FINISH, event_name=OCSP_REQUEST, .* request_url=http://aia.demo.sk.ee/esteid2018, .* result=SUCCESS.*");
    }

}
