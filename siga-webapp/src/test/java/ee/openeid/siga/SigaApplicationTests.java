package ee.openeid.siga;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.auth.filter.hmac.HmacSignature;
import ee.openeid.siga.service.signature.hashcode.DetachedDataFileContainer;
import ee.openeid.siga.webapp.json.*;
import org.apache.commons.io.IOUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
@SpringBootTest(classes = {SigaApplication.class}, webEnvironment = RANDOM_PORT, properties = {"siga.security.hmac.expiration=120", "siga.security.hmac.clock-skew=2"})
@AutoConfigureMockMvc
public class SigaApplicationTests {

    private final static String DEFAULT_HMAC_ALGO = "HmacSHA256";
    private final static String HMAC_SHARED_SECRET = "746573745365637265744b6579303031";
    private final static String REQUESTING_SERVICE_UUID = "a7fd7728-a3ea-4975-bfab-f240a67e894f";
    private final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/sign_ESTEID2018.p12", "1234".toCharArray());
    @Autowired
    MockMvc mockMvc;
    private String xAuthorizationTimestamp;
    @Autowired
    private ObjectMapper objectMapper;

    @Before
    public void setup() {
        xAuthorizationTimestamp = valueOf(now().getEpochSecond());
    }

    @Test
    public void hashcodeModifyingContainerFlow() throws Exception {
        String containerId = createHashcodeContainer();
        DetachedDataFileContainer originalContainer = getHashcodeContainer(containerId);
        Assert.assertEquals(0, originalContainer.getSignatures().size());
        Assert.assertEquals(1, originalContainer.getDataFiles().size());
        addHashcodeDataFile(containerId);
        DetachedDataFileContainer updatedContainer = getHashcodeContainer(containerId);
        Assert.assertEquals(0, updatedContainer.getSignatures().size());
        Assert.assertEquals(2, updatedContainer.getDataFiles().size());
        deleteHashcodeDataFile(containerId, updatedContainer.getDataFiles().get(0).getFileName());
        DetachedDataFileContainer updatedContainer2 = getHashcodeContainer(containerId);
        Assert.assertEquals(0, updatedContainer2.getSignatures().size());
        Assert.assertEquals(1, updatedContainer2.getDataFiles().size());
    }

    @Test
    public void dataFileModifyingContainerFlow() throws Exception {
        String containerId = createContainer();
        Container originalContainer = getContainer(containerId);
        Assert.assertEquals(0, originalContainer.getSignatures().size());
        Assert.assertEquals(1, originalContainer.getDataFiles().size());
        addDataFile(containerId);
        Container updatedContainer = getContainer(containerId);
        Assert.assertEquals(2, updatedContainer.getDataFiles().size());
        deleteDataFile(containerId, updatedContainer.getDataFiles().get(0).getName());
        Container updatedContainer2 = getContainer(containerId);
        Assert.assertEquals(1, updatedContainer2.getDataFiles().size());
    }

    @Test
    public void getAnotherUserContainer() throws Exception {

        String containerId = uploadHashcodeContainer();
        DetachedDataFileContainer originalContainer = getHashcodeContainer(containerId);
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
        assertSignedContainer(containerId);
    }

    @Test
    public void remoteSigningFlow() throws Exception {
        String containerId = uploadContainer();
        List<Signature> signatures = getSignatures(containerId);
        GetContainerSignatureDetailsResponse signatureResponse = getSignature(containerId, signatures.get(0).getGeneratedSignatureId());
        Assert.assertEquals("S0", signatureResponse.getId());
        Assert.assertEquals(1, signatures.size());
        Container originalContainer = getContainer(containerId);
        Assert.assertEquals(1, originalContainer.getSignatures().size());
        Assert.assertEquals(2, originalContainer.getDataFiles().size());

        CreateContainerRemoteSigningResponse startRemoteSigningResponse = startRemoteSigning(containerId);
        byte[] dataToSign = Base64.getDecoder().decode(startRemoteSigningResponse.getDataToSign());
        byte[] signedData = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.findByAlgorithm(startRemoteSigningResponse.getDigestAlgorithm()), dataToSign);
        String signatureValue = new String(Base64.getEncoder().encode(signedData));
        finalizeRemoteSigning("/containers/" + containerId + "/remotesigning/" + startRemoteSigningResponse.getGeneratedSignatureId(), signatureValue);
        assertSignedContainer(containerId);
    }

    @Test
    public void mobileIdHashcodeSigningFlow() throws Exception {
        String containerId = uploadHashcodeContainer();
        List<Signature> signatures = getHashcodeSignatures(containerId);

        Assert.assertEquals(1, signatures.size());
        DetachedDataFileContainer originalContainer = getHashcodeContainer(containerId);
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
        assertHashcodeSignedContainer(containerId);
    }

    @Test
    public void remoteHashcodeSigningFlow() throws Exception {
        String containerId = uploadHashcodeContainer();
        List<Signature> signatures = getHashcodeSignatures(containerId);
        GetHashcodeContainerSignatureDetailsResponse signatureResponse = getHashcodeSignature(containerId, signatures.get(0).getGeneratedSignatureId());
        Assert.assertEquals("id-a9fae00496ae203a6a8b92adbe762bd3", signatureResponse.getId());

        Assert.assertEquals(1, signatures.size());
        DetachedDataFileContainer originalContainer = getHashcodeContainer(containerId);
        Assert.assertEquals(1, originalContainer.getSignatures().size());
        Assert.assertEquals(2, originalContainer.getDataFiles().size());
        CreateHashcodeContainerRemoteSigningResponse startRemoteSigningResponse = startHashcodeRemoteSigning(containerId);
        byte[] dataToSign = Base64.getDecoder().decode(startRemoteSigningResponse.getDataToSign());
        byte[] signedData = pkcs12Esteid2018SignatureToken.sign(DigestAlgorithm.findByAlgorithm(startRemoteSigningResponse.getDigestAlgorithm()), dataToSign);
        String signatureValue = new String(Base64.getEncoder().encode(signedData));
        finalizeRemoteSigning("/hashcodecontainers/" + containerId + "/remotesigning/" + startRemoteSigningResponse.getGeneratedSignatureId(), signatureValue);
        assertHashcodeSignedContainer(containerId);
    }

    private void assertHashcodeSignedContainer(String containerId) throws Exception {
        DetachedDataFileContainer container = getHashcodeContainer(containerId);
        Assert.assertEquals(2, container.getSignatures().size());
        Assert.assertEquals(2, container.getDataFiles().size());

        List<Signature> signatures = getHashcodeSignatures(containerId);

        Assert.assertEquals(2, signatures.size());
        ValidationConclusion validationConclusion = getHashcodeValidationConclusion(containerId);
        Assert.assertEquals(Integer.valueOf(2), validationConclusion.getValidSignaturesCount());
        Assert.assertEquals(Integer.valueOf(2), validationConclusion.getSignaturesCount());
    }

    private void assertSignedContainer(String containerId) throws Exception {
        Container container = getContainer(containerId);
        Assert.assertEquals(2, container.getSignatures().size());
        Assert.assertEquals(2, container.getDataFiles().size());

        List<Signature> signatures = getSignatures(containerId);

        Assert.assertEquals(2, signatures.size());
        ValidationConclusion validationConclusion = getValidationConclusion(containerId);
        Assert.assertEquals(Integer.valueOf(2), validationConclusion.getValidSignaturesCount());
        Assert.assertEquals(Integer.valueOf(2), validationConclusion.getSignaturesCount());
    }

    private List<HashcodeDataFile> getHashcodeDataFiles(String containerId) throws Exception {
        GetHashcodeContainerDataFilesResponse signaturesResponse = (GetHashcodeContainerDataFilesResponse) getRequest("/hashcodecontainers/" + containerId + "/datafiles", GetHashcodeContainerDataFilesResponse.class);
        return signaturesResponse.getDataFiles();
    }

    private List<DataFile> getDataFiles(String containerId) throws Exception {
        GetContainerDataFilesResponse signaturesResponse = (GetContainerDataFilesResponse) getRequest("/containers/" + containerId + "/datafiles", GetContainerDataFilesResponse.class);
        return signaturesResponse.getDataFiles();
    }

    private List<Signature> getHashcodeSignatures(String containerId) throws Exception {
        GetHashcodeContainerSignaturesResponse signaturesResponse = (GetHashcodeContainerSignaturesResponse) getRequest("/hashcodecontainers/" + containerId + "/signatures", GetHashcodeContainerSignaturesResponse.class);
        return signaturesResponse.getSignatures();
    }

    private List<Signature> getSignatures(String containerId) throws Exception {
        GetContainerSignaturesResponse signaturesResponse = (GetContainerSignaturesResponse) getRequest("/containers/" + containerId + "/signatures", GetContainerSignaturesResponse.class);
        return signaturesResponse.getSignatures();
    }

    private GetContainerSignatureDetailsResponse getSignature(String containerId, String signatureId) throws Exception {
        return (GetContainerSignatureDetailsResponse) getRequest("/containers/" + containerId + "/signatures/" + signatureId, GetContainerSignatureDetailsResponse.class);
    }

    private GetHashcodeContainerSignatureDetailsResponse getHashcodeSignature(String containerId, String signatureId) throws Exception {
        return (GetHashcodeContainerSignatureDetailsResponse) getRequest("/hashcodecontainers/" + containerId + "/signatures/" + signatureId, GetHashcodeContainerSignatureDetailsResponse.class);
    }

    private ValidationConclusion getHashcodeValidationConclusion(String containerId) throws Exception {
        GetHashcodeContainerValidationReportResponse response = (GetHashcodeContainerValidationReportResponse) getRequest("/hashcodecontainers/" + containerId + "/validationreport", GetHashcodeContainerValidationReportResponse.class);
        return response.getValidationConclusion();
    }

    private ValidationConclusion getValidationConclusion(String containerId) throws Exception {
        GetContainerValidationReportResponse response = (GetContainerValidationReportResponse) getRequest("/containers/" + containerId + "/validationreport", GetContainerValidationReportResponse.class);
        return response.getValidationConclusion();
    }

    private DetachedDataFileContainer getHashcodeContainer(String containerId) throws Exception {
        GetHashcodeContainerResponse originalContainer = (GetHashcodeContainerResponse) getRequest("/hashcodecontainers/" + containerId, GetHashcodeContainerResponse.class);

        DetachedDataFileContainer detachedDataFileContainer = new DetachedDataFileContainer();
        detachedDataFileContainer.open(new ByteArrayInputStream(Base64.getDecoder().decode(originalContainer.getContainer())));
        return detachedDataFileContainer;
    }

    private Container getContainer(String containerId) throws Exception {
        GetContainerResponse originalContainer = (GetContainerResponse) getRequest("/containers/" + containerId, GetContainerResponse.class);
        return ContainerBuilder.aContainer().withConfiguration(Configuration.of(Configuration.Mode.TEST)).fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(originalContainer.getContainer().getBytes()))).build();
    }

    private String getHashcodeMobileIdStatus(String containerId, String signatureId) throws Exception {
        GetHashcodeContainerMobileIdSigningStatusResponse response = (GetHashcodeContainerMobileIdSigningStatusResponse) getRequest("/hashcodecontainers/" + containerId + "/mobileidsigning/" + signatureId + "/status", GetHashcodeContainerMobileIdSigningStatusResponse.class);
        return response.getMidStatus();
    }

    private String getMobileIdStatus(String containerId, String signatureId) throws Exception {
        GetContainerMobileIdSigningStatusResponse response = (GetContainerMobileIdSigningStatusResponse) getRequest("/containers/" + containerId + "/mobileidsigning/" + signatureId + "/status", GetContainerMobileIdSigningStatusResponse.class);
        return response.getMidStatus();
    }

    private Object startRemoteSigning(String url, Class responseObject) throws Exception {
        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");
        request.put("signingCertificate", new String(Base64.getEncoder().encode(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded())));
        JSONArray roles = new JSONArray();
        roles.put("Manager");
        roles.put("Developer");
        request.put("roles", roles);
        return postRequest(url, request, responseObject);
    }

    private CreateHashcodeContainerRemoteSigningResponse startHashcodeRemoteSigning(String containerId) throws Exception {
        return (CreateHashcodeContainerRemoteSigningResponse) startRemoteSigning("/hashcodecontainers/" + containerId + "/remotesigning", CreateHashcodeContainerRemoteSigningResponse.class);
    }

    private CreateContainerRemoteSigningResponse startRemoteSigning(String containerId) throws Exception {
        return (CreateContainerRemoteSigningResponse) startRemoteSigning("/containers/" + containerId + "/remotesigning", CreateContainerRemoteSigningResponse.class);
    }

    private void finalizeRemoteSigning(String url, String signatureValue) throws Exception {
        JSONObject request = new JSONObject();
        request.put("signatureValue", signatureValue);
        putRequest(url, request);
    }

    private Object startMobileSigning(String url, Class responseObject) throws Exception {
        JSONObject request = new JSONObject();
        request.put("personIdentifier", "60001019906");
        request.put("phoneNo", "+37200000766");
        request.put("country", "EE");
        request.put("language", "EST");
        request.put("signatureProfile", "LT");
        return postRequest(url, request, responseObject);
    }

    private String startHashcodeMobileSigning(String containerId) throws Exception {
        CreateHashcodeContainerMobileIdSigningResponse response = (CreateHashcodeContainerMobileIdSigningResponse) startMobileSigning("/hashcodecontainers/" + containerId + "/mobileidsigning", CreateHashcodeContainerMobileIdSigningResponse.class);
        return response.getGeneratedSignatureId();
    }

    private String startMobileSigning(String containerId) throws Exception {
        CreateContainerMobileIdSigningResponse response = (CreateContainerMobileIdSigningResponse) startMobileSigning("/containers/" + containerId + "/mobileidsigning", CreateContainerMobileIdSigningResponse.class);
        return response.getGeneratedSignatureId();
    }

    private String uploadHashcodeContainer() throws Exception {
        String container = IOUtils.toString(getFileInputStream("hashcode.asice"), Charset.defaultCharset());
        UploadHashcodeContainerResponse containerResponse = (UploadHashcodeContainerResponse) uploadContainer(container, "/upload/hashcodecontainers", null, UploadHashcodeContainerResponse.class);
        return containerResponse.getContainerId();
    }

    private String uploadContainer() throws Exception {
        String container = IOUtils.toString(getFileInputStream("datafile.asice"), Charset.defaultCharset());
        UploadContainerResponse containerResponse = (UploadContainerResponse) uploadContainer(container, "/upload/containers", "datafile.asice", UploadContainerResponse.class);
        return containerResponse.getContainerId();
    }

    private Object uploadContainer(String container, String url, String containerName, Class responseObject) throws Exception {
        JSONObject request = new JSONObject();
        if (containerName != null) {
            request.put("containerName", containerName);
        }
        request.put("container", container);
        return postRequest(url, request, responseObject);
    }

    private String createHashcodeContainer() throws Exception {
        JSONObject request = new JSONObject();
        JSONObject dataFile = new JSONObject();
        JSONArray dataFiles = new JSONArray();
        dataFile.put("fileName", "test.txt");
        dataFile.put("fileHashSha256", "K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=");
        dataFile.put("fileHashSha512", "vSsar3708Jvp9Szi2NWZZ02Bqp1qRCFpbcTZPdBhnWgs5WtNZKnvCXdhztmeD2cmW192CF5bDufKRpayrW/isg==");
        dataFile.put("fileSize", 10);
        dataFiles.put(dataFile);
        request.put("dataFiles", dataFiles);
        CreateHashcodeContainerResponse response = (CreateHashcodeContainerResponse) postRequest("/hashcodecontainers", request, CreateHashcodeContainerResponse.class);
        return response.getContainerId();
    }

    private String createContainer() throws Exception {
        JSONObject request = new JSONObject();
        JSONObject dataFile = new JSONObject();
        JSONArray dataFiles = new JSONArray();
        request.put("containerName", "test.asice");
        dataFile.put("fileName", "test.txt");
        dataFile.put("fileContent", "K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=");
        dataFiles.put(dataFile);
        request.put("dataFiles", dataFiles);
        CreateContainerResponse response = (CreateContainerResponse) postRequest("/containers", request, CreateContainerResponse.class);
        return response.getContainerId();
    }


    private void addDataFile(String containerId) throws Exception {
        JSONObject request = new JSONObject();
        JSONObject dataFile = new JSONObject();
        dataFile.put("fileName", "test1.txt");
        dataFile.put("fileContent", "WxFhjC5EAnh30M0JIe0Wa58Xb1BYf8kedTTdKUbbd9Y=");
        request.put("dataFile", dataFile);
        postRequest("/containers/" + containerId + "/datafiles", request, CreateContainerDataFileResponse.class);
    }

    private void addHashcodeDataFile(String containerId) throws Exception {
        JSONObject request = new JSONObject();
        JSONObject dataFile = new JSONObject();
        dataFile.put("fileName", "test1.txt");
        dataFile.put("fileHashSha256", "WxFhjC5EAnh30M0JIe0Wa58Xb1BYf8kedTTdKUbbd9Y=");
        dataFile.put("fileHashSha512", "HD6Xh+Y6oIZnXv4XqbKxrb6t3RkoPYv+NkqOBE8MwkssuATRE2aFBp8Nm9kp/Xn5a4l2Ki8QkX5qIUlbXQgO4Q==");
        dataFile.put("fileSize", 10);
        request.put("dataFile", dataFile);
        postRequest("/hashcodecontainers/" + containerId + "/datafiles", request, CreateHashcodeContainerDataFileResponse.class);
    }

    private void deleteDataFile(String containerId, String dataFileName) throws Exception {
        deleteRequest("/containers/" + containerId + "/datafiles/" + dataFileName);
    }

    private void deleteHashcodeDataFile(String containerId, String dataFileName) throws Exception {
        deleteRequest("/hashcodecontainers/" + containerId + "/datafiles/" + dataFileName);
    }

    private void deleteRequest(String url) throws Exception {
        JSONObject request = new JSONObject();
        String signature = getSignature("DELETE", url, request.toString());
        MockHttpServletRequestBuilder builder = delete(url);
        mockMvc.perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());
    }

    private Object postRequest(String url, JSONObject request, Class responseObject) throws Exception {
        String signature = getSignature("POST", url, request.toString());
        MockHttpServletRequestBuilder builder = post(url);

        ResultActions response = mockMvc.perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());
        return objectMapper.readValue(response.andReturn().getResponse().getContentAsString(), responseObject);
    }

    private void putRequest(String url, JSONObject request) throws Exception {
        String signature = getSignature("PUT", url, request.toString());
        MockHttpServletRequestBuilder builder = put(url);

        mockMvc.perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());
    }

    private Object getRequest(String url, Class responseObject) throws Exception {
        JSONObject request = new JSONObject();
        String signature = getSignature("GET", url, request.toString());
        MockHttpServletRequestBuilder builder = get(url);
        ResultActions response = mockMvc.perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());
        return objectMapper.readValue(response.andReturn().getResponse().getContentAsString(), responseObject);
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

    private InputStream getFileInputStream(String name) throws IOException {
        Path documentPath = Paths.get(new ClassPathResource(name).getURI());
        return new ByteArrayInputStream(Base64.getEncoder().encode(Files.readAllBytes(documentPath)));
    }

}

