package ee.openeid.siga;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.auth.SecurityConfiguration;
import ee.openeid.siga.auth.filter.hmac.HmacSignature;
import ee.openeid.siga.auth.properties.VaultProperties;
import ee.openeid.siga.service.signature.hashcode.DetachedDataFileContainer;
import ee.openeid.siga.webapp.json.*;
import org.apache.commons.io.IOUtils;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponseSupport;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

import static ee.openeid.siga.auth.filter.hmac.HmacHeader.*;
import static java.lang.String.valueOf;
import static java.time.Instant.now;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ActiveProfiles({"test", "digidoc4jTest"})
@SpringBootTest(classes = {SigaApplication.class, SigaApplicationTests.TestConfiguration.class}, webEnvironment = RANDOM_PORT,
        properties = {"spring.main.allow-bean-definition-overriding=true",
                "siga.security.hmac.expiration=120",
                "siga.security.hmac.clock-skew=2"})

@AutoConfigureMockMvc
public class SigaApplicationTests {

    private final static String DEFAULT_HMAC_ALGO = "HmacSHA256";
    private final static String HMAC_SHARED_SECRET = "746573745365637265744b6579303031";
    private final static String REQUESTING_SERVICE_UUID = "a7fd7728-a3ea-4975-bfab-f240a67e894f";
    private String xAuthorizationTimestamp;
    private final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/sign_ESTEID2018.p12", "1234".toCharArray());

    @Autowired
    MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    VaultTemplate vaultTemplate;

    @Before
    public void setup() {
        xAuthorizationTimestamp = valueOf(now().getEpochSecond());
    }

    @Test
    public void getAnotherUserContainer() throws Exception {

        String containerId = uploadContainer();
        DetachedDataFileContainer originalContainer = getContainer(containerId);
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
        List<Signature> signatures = getSignatureList(containerId);
        DetachedDataFileContainer originalContainer = getContainer(containerId);
        Assert.assertEquals(1, originalContainer.getSignatures().size());
        Assert.assertEquals(2, originalContainer.getDataFiles().size());
        startMobileSigning(containerId);
        String mobileFirstStatus = getMobileIdStatus(containerId);
        Assert.assertEquals("OUTSTANDING_TRANSACTION", mobileFirstStatus);
        Thread.sleep(8000);
        String mobileStatus = getMobileIdStatus(containerId);
        Assert.assertEquals("SIGNATURE", mobileStatus);
        assertSignedContainer(containerId);
    }

    @Test
    public void remoteSigningFlow() throws Exception {
        String containerId = uploadContainer();
        List<Signature> signatures = getSignatureList(containerId);
        DetachedDataFileContainer originalContainer = getContainer(containerId);
        Assert.assertEquals(1, originalContainer.getSignatures().size());
        Assert.assertEquals(2, originalContainer.getDataFiles().size());
        CreateHashcodeContainerRemoteSigningResponse startRemoteSigningResponse = startRemoteSigning(containerId);
        byte[] dataToSign = Base64.getDecoder().decode(startRemoteSigningResponse.getDataToSign());
        byte[] signedData = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.findByAlgorithm(startRemoteSigningResponse.getDigestAlgorithm()), dataToSign);
        String signatureValue = new String(Base64.getEncoder().encode(signedData));
        finalizeRemoteSigning(containerId, signatureValue);
        assertSignedContainer(containerId);
    }

    private void assertSignedContainer(String containerId) throws Exception {
        DetachedDataFileContainer container = getContainer(containerId);
        Assert.assertEquals(2, container.getSignatures().size());
        Assert.assertEquals(2, container.getDataFiles().size());
        List<Signature> signatures = getSignatureList(containerId);
        Assert.assertEquals(2, signatures.size());
        ValidationConclusion validationConclusion = getValidationConclusion(containerId);
        Assert.assertEquals(Integer.valueOf(2), validationConclusion.getValidSignaturesCount());
        Assert.assertEquals(Integer.valueOf(2), validationConclusion.getSignaturesCount());
    }

    private List<Signature> getSignatureList(String containerId) throws Exception {
        JSONObject request = new JSONObject();
        String signature = getSignature("GET", "/hashcodecontainers/" + containerId + "/signatures", request.toString());
        MockHttpServletRequestBuilder builder = get("/hashcodecontainers/" + containerId + "/signatures");
        ResultActions response = mockMvc.perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());
        GetHashcodeContainerSignaturesResponse signatureListResponse = objectMapper.readValue(response.andReturn().getResponse().getContentAsString(), GetHashcodeContainerSignaturesResponse.class);
        return signatureListResponse.getSignatures();
    }

    private ValidationConclusion getValidationConclusion(String containerId) throws Exception {

        JSONObject request = new JSONObject();
        String signature = getSignature("GET", "/hashcodecontainers/" + containerId + "/validationreport", request.toString());
        MockHttpServletRequestBuilder builder = get("/hashcodecontainers/" + containerId + "/validationreport");
        ResultActions response = mockMvc.perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());

        GetHashcodeContainerValidationReportResponse reportResponse = objectMapper.readValue(response.andReturn().getResponse().getContentAsString(), GetHashcodeContainerValidationReportResponse.class);
        return reportResponse.getValidationConclusion();
    }

    private DetachedDataFileContainer getContainer(String containerId) throws Exception {
        JSONObject request = new JSONObject();
        String signature = getSignature("GET", "/hashcodecontainers/" + containerId, request.toString());
        MockHttpServletRequestBuilder builder = get("/hashcodecontainers/" + containerId);
        ResultActions response = mockMvc.perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());
        GetHashcodeContainerResponse getHashcodeContainerResponse = objectMapper.readValue(response.andReturn().getResponse().getContentAsString(), GetHashcodeContainerResponse.class);
        DetachedDataFileContainer detachedDataFileContainer = new DetachedDataFileContainer();
        detachedDataFileContainer.open(new ByteArrayInputStream(Base64.getDecoder().decode(getHashcodeContainerResponse.getContainer())));
        return detachedDataFileContainer;
    }

    private String getMobileIdStatus(String containerId) throws Exception {
        JSONObject request = new JSONObject();
        String signature = getSignature("GET", "/hashcodecontainers/" + containerId + "/mobileidsigning/status", request.toString());
        MockHttpServletRequestBuilder builder = get("/hashcodecontainers/" + containerId + "/mobileidsigning/status");
        ResultActions response = mockMvc.perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());
        return objectMapper.readValue(response.andReturn().getResponse().getContentAsString(), GetHashcodeContainerMobileIdSigningStatusResponse.class).getMidStatus();
    }

    private CreateHashcodeContainerRemoteSigningResponse startRemoteSigning(String containerId) throws Exception {
        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");
        request.put("signingCertificate", new String(Base64.getEncoder().encode(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded())));
        String signature = getSignature("POST", "/hashcodecontainers/" + containerId + "/remotesigning", request.toString());
        MockHttpServletRequestBuilder builder = post("/hashcodecontainers/" + containerId + "/remotesigning");

        ResultActions response = mockMvc.perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());
        return objectMapper.readValue(response.andReturn().getResponse().getContentAsString(), CreateHashcodeContainerRemoteSigningResponse.class);
    }

    private void finalizeRemoteSigning(String containerId, String signatureValue) throws Exception {
        JSONObject request = new JSONObject();
        request.put("signatureValue", signatureValue);

        String signature = getSignature("PUT", "/hashcodecontainers/" + containerId + "/remotesigning", request.toString());
        MockHttpServletRequestBuilder builder = put("/hashcodecontainers/" + containerId + "/remotesigning");

        mockMvc.perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());

    }

    private void startMobileSigning(String containerId) throws Exception {
        JSONObject request = new JSONObject();
        request.put("personIdentifier", "60001019906");
        request.put("phoneNo", "+37200000766");
        request.put("originCountry", "EE");
        request.put("language", "EST");
        request.put("serviceName", "Testimine");
        request.put("signatureProfile", "LT");
        String signature = getSignature("POST", "/hashcodecontainers/" + containerId + "/mobileidsigning", request.toString());
        MockHttpServletRequestBuilder builder = post("/hashcodecontainers/" + containerId + "/mobileidsigning");

        mockMvc.perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());
    }

    private String uploadContainer() throws Exception {
        JSONObject request = new JSONObject();
        String container = IOUtils.toString(getFileInputStream(), Charset.defaultCharset());
        request.put("container", container);
        String signature = getSignature("POST", "/upload/hashcodecontainers", request.toString());
        MockHttpServletRequestBuilder builder = post("/upload/hashcodecontainers");

        ResultActions response = mockMvc.perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());
        return objectMapper.readValue(response.andReturn().getResponse().getContentAsString(), UploadHashcodeContainerResponse.class).getContainerId();
    }

    private MockHttpServletRequestBuilder buildRequest(MockHttpServletRequestBuilder builder, String signature, JSONObject request, String serviceUUID) {
        return builder.accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), serviceUUID)
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), signature)
                .content(request.toString().getBytes());
    }

    private String getSignature(String requestMethod, String uri, String payload) throws Exception {
        return HmacSignature.builder()
                .macAlgorithm(DEFAULT_HMAC_ALGO)
                .serviceUuid(REQUESTING_SERVICE_UUID)
                .timestamp(xAuthorizationTimestamp)
                .requestMethod(requestMethod)
                .uri(uri)
                .payload(payload.getBytes())
                .build().getSignature(HMAC_SHARED_SECRET);
    }

    private InputStream getFileInputStream() throws IOException {
        Path documentPath = Paths.get(new ClassPathResource("hashcode.asice").getURI());
        return new ByteArrayInputStream(Base64.getEncoder().encode(Files.readAllBytes(documentPath)));
    }

    @Profile("test")
    @Configuration
    @Import(SecurityConfiguration.class)

    static class TestConfiguration {

        @Primary
        @Bean
        public VaultTemplate vaultTemplate() {
            VaultTemplate vaultTemplate = Mockito.mock(VaultTemplate.class);
            VaultProperties svp = new VaultProperties();
            svp.setJasyptEncryptionConf(new VaultProperties.JasyptEncryptionConf());
            svp.getJasyptEncryptionConf().setAlgorithm("PBEWithMD5AndDES");
            svp.getJasyptEncryptionConf().setKey("encryptorKey");
            VaultResponseSupport<VaultProperties> vrs = new VaultResponseSupport<>();
            vrs.setData(svp);
            Mockito.when(vaultTemplate.read("dev/siga", VaultProperties.class)).thenReturn(vrs);
            return vaultTemplate;
        }
    }

}

