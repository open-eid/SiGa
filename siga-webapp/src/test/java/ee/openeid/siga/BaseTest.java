package ee.openeid.siga;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.auth.filter.hmac.HmacSignature;
import ee.openeid.siga.common.model.CertificateStatus;
import ee.openeid.siga.common.model.ServiceType;
import ee.openeid.siga.service.signature.hashcode.HashcodeContainer;
import ee.openeid.siga.webapp.json.CreateContainerDataFileResponse;
import ee.openeid.siga.webapp.json.CreateContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
import ee.openeid.siga.webapp.json.CreateContainerResponse;
import ee.openeid.siga.webapp.json.CreateContainerSmartIdCertificateChoiceResponse;
import ee.openeid.siga.webapp.json.CreateContainerSmartIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerDataFileResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerRemoteSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerSmartIdCertificateChoiceResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerSmartIdSigningResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerValidationReportResponse;
import ee.openeid.siga.webapp.json.DataFile;
import ee.openeid.siga.webapp.json.GetContainerDataFilesResponse;
import ee.openeid.siga.webapp.json.GetContainerMobileIdSigningStatusResponse;
import ee.openeid.siga.webapp.json.GetContainerResponse;
import ee.openeid.siga.webapp.json.GetContainerSignatureDetailsResponse;
import ee.openeid.siga.webapp.json.GetContainerSignaturesResponse;
import ee.openeid.siga.webapp.json.GetContainerSmartIdCertificateChoiceStatusResponse;
import ee.openeid.siga.webapp.json.GetContainerSmartIdSigningStatusResponse;
import ee.openeid.siga.webapp.json.GetContainerValidationReportResponse;
import ee.openeid.siga.webapp.json.GetHashcodeContainerDataFilesResponse;
import ee.openeid.siga.webapp.json.GetHashcodeContainerMobileIdSigningStatusResponse;
import ee.openeid.siga.webapp.json.GetHashcodeContainerResponse;
import ee.openeid.siga.webapp.json.GetHashcodeContainerSignaturesResponse;
import ee.openeid.siga.webapp.json.GetHashcodeContainerSmartIdCertificateChoiceStatusResponse;
import ee.openeid.siga.webapp.json.GetHashcodeContainerSmartIdSigningStatusResponse;
import ee.openeid.siga.webapp.json.GetHashcodeContainerValidationReportResponse;
import ee.openeid.siga.webapp.json.HashcodeDataFile;
import ee.openeid.siga.webapp.json.Signature;
import ee.openeid.siga.webapp.json.UploadContainerResponse;
import ee.openeid.siga.webapp.json.UploadHashcodeContainerResponse;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import org.apache.commons.io.IOUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
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
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static ee.openeid.siga.auth.filter.hmac.HmacHeader.X_AUTHORIZATION_SERVICE_UUID;
import static ee.openeid.siga.auth.filter.hmac.HmacHeader.X_AUTHORIZATION_SIGNATURE;
import static ee.openeid.siga.auth.filter.hmac.HmacHeader.X_AUTHORIZATION_TIMESTAMP;
import static java.lang.String.valueOf;
import static java.time.Instant.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public abstract class BaseTest extends BaseTestLoggingAssertion {

    protected static final String CERTIFICATE = "CERTIFICATE";
    protected static final String SIGNATURE = "SIGNATURE";

    protected final static String DEFAULT_HMAC_ALGO = "HmacSHA256";
    private final static String DEFAULT_HMAC_SHARED_SECRET = "746573745365637265744b6579303031";
    private final static String DEFAULT_REQUESTING_SERVICE_UUID = "a7fd7728-a3ea-4975-bfab-f240a67e894f";
    protected final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/sign_ECC_from_TEST_of_ESTEID2018.p12", "1234".toCharArray());

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;

    protected String xAuthorizationTimestamp;

    @BeforeEach
    public void setup() {
        xAuthorizationTimestamp = valueOf(now().getEpochSecond());
    }

    protected void assertHashcodeSignedContainer(String containerId, int validSignatureCount) throws Exception {
        HashcodeContainer container = getHashcodeContainer(containerId);
        assertEquals(2, container.getSignatures().size());
        assertEquals(2, container.getDataFiles().size());

        List<Signature> signatures = getHashcodeSignatures(containerId);

        assertEquals(2, signatures.size());
        ValidationConclusion validationConclusion = getHashcodeValidationConclusion(containerId);
        assertEquals(Integer.valueOf(validSignatureCount), validationConclusion.getValidSignaturesCount());
        assertEquals(Integer.valueOf(2), validationConclusion.getSignaturesCount());
    }

    protected void assertSignedContainer(String containerId, int validSignatureCount) throws Exception {
        Container container = getContainer(containerId);
        assertEquals(2, container.getSignatures().size());
        assertEquals(1, container.getDataFiles().size());
        List<Signature> signatures = getSignatures(containerId);

        assertEquals(2, signatures.size());
        ValidationConclusion validationConclusion = getValidationConclusion(containerId);
        assertEquals(Integer.valueOf(validSignatureCount), validationConclusion.getValidSignaturesCount());
        assertEquals(Integer.valueOf(2), validationConclusion.getSignaturesCount());
    }

    protected List<HashcodeDataFile> getHashcodeDataFiles(String containerId) throws Exception {
        GetHashcodeContainerDataFilesResponse signaturesResponse = getRequest(
                "/hashcodecontainers/" + containerId + "/datafiles",
                GetHashcodeContainerDataFilesResponse.class
        );
        return signaturesResponse.getDataFiles();
    }

    protected List<DataFile> getDataFiles(String containerId) throws Exception {
        GetContainerDataFilesResponse signaturesResponse = getRequest(
                "/containers/" + containerId + "/datafiles",
                GetContainerDataFilesResponse.class
        );
        return signaturesResponse.getDataFiles();
    }

    protected List<Signature> getHashcodeSignatures(String containerId) throws Exception {
        GetHashcodeContainerSignaturesResponse signaturesResponse = getRequest(
                "/hashcodecontainers/" + containerId + "/signatures",
                GetHashcodeContainerSignaturesResponse.class
        );
        return signaturesResponse.getSignatures();
    }

    protected List<Signature> getSignatures(String containerId) throws Exception {
        GetContainerSignaturesResponse signaturesResponse = getRequest(
                "/containers/" + containerId + "/signatures",
                GetContainerSignaturesResponse.class
        );
        return signaturesResponse.getSignatures();
    }

    protected GetContainerSignatureDetailsResponse getSignature(String containerId, String signatureId) throws Exception {
        return getRequest(
                "/containers/" + containerId + "/signatures/" + signatureId,
                GetContainerSignatureDetailsResponse.class
        );
    }

    protected GetContainerSignatureDetailsResponse getHashcodeSignature(String containerId, String signatureId) throws Exception {
        return getRequest(
                "/hashcodecontainers/" + containerId + "/signatures/" + signatureId,
                GetContainerSignatureDetailsResponse.class
        );
    }

    protected ValidationConclusion getHashcodeValidationConclusion(String containerId) throws Exception {
        GetHashcodeContainerValidationReportResponse response = getRequest(
                "/hashcodecontainers/" + containerId + "/validationreport",
                GetHashcodeContainerValidationReportResponse.class
        );
        return response.getValidationConclusion();
    }

    protected ValidationConclusion getValidationConclusion(String containerId) throws Exception {
        GetContainerValidationReportResponse response = getRequest(
                "/containers/" + containerId + "/validationreport",
                GetContainerValidationReportResponse.class
        );
        return response.getValidationConclusion();
    }

    protected HashcodeContainer getHashcodeContainer(String containerId) throws Exception {
        GetHashcodeContainerResponse originalContainer = getRequest(
                "/hashcodecontainers/" + containerId,
                GetHashcodeContainerResponse.class
        );

        HashcodeContainer hashcodeContainer = new HashcodeContainer(getServiceType());
        hashcodeContainer.open(Base64.getDecoder().decode(originalContainer.getContainer()));
        return hashcodeContainer;
    }

    protected Container getContainer(String containerId) throws Exception {
        GetContainerResponse originalContainer = getRequest(
                "/containers/" + containerId,
                GetContainerResponse.class
        );
        return ContainerBuilder.aContainer()
                .withConfiguration(Configuration.of(Configuration.Mode.TEST))
                .fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(originalContainer.getContainer().getBytes())))
                .build();
    }

    protected String getHashcodeMobileIdStatus(String containerId, String signatureId) throws Exception {
        GetHashcodeContainerMobileIdSigningStatusResponse response = getRequest(
                "/hashcodecontainers/" + containerId + "/mobileidsigning/" + signatureId + "/status",
                GetHashcodeContainerMobileIdSigningStatusResponse.class
        );
        return response.getMidStatus();
    }

    protected String getMobileIdStatus(String containerId, String signatureId) throws Exception {
        GetContainerMobileIdSigningStatusResponse response = getRequest(
                "/containers/" + containerId + "/mobileidsigning/" + signatureId + "/status",
                GetContainerMobileIdSigningStatusResponse.class
        );
        return response.getMidStatus();
    }

    protected CertificateStatus getHashcodeCertificateChoiceStatus(String containerId, String certificateId) throws Exception {
        GetHashcodeContainerSmartIdCertificateChoiceStatusResponse response = getRequest(
                "/hashcodecontainers/" + containerId + "/smartidsigning/certificatechoice/" + certificateId + "/status",
                GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class
        );
        return CertificateStatus.builder()
                .status(response.getSidStatus())
                .documentNumber(response.getDocumentNumber()).build();
    }

    protected CertificateStatus getCertificateChoiceStatus(String containerId, String certificateId) throws Exception {
        GetContainerSmartIdCertificateChoiceStatusResponse response = getRequest(
                "/containers/" + containerId + "/smartidsigning/certificatechoice/" + certificateId + "/status",
                GetContainerSmartIdCertificateChoiceStatusResponse.class
        );
        return CertificateStatus.builder()
                .status(response.getSidStatus())
                .documentNumber(response.getDocumentNumber()).build();
    }

    protected String getHashcodeSmartIdStatus(String containerId, String signatureId) throws Exception {
        GetHashcodeContainerSmartIdSigningStatusResponse response = getRequest(
                "/hashcodecontainers/" + containerId + "/smartidsigning/" + signatureId + "/status",
                GetHashcodeContainerSmartIdSigningStatusResponse.class
        );
        return response.getSidStatus();
    }

    protected String getSmartIdStatus(String containerId, String signatureId) throws Exception {
        GetContainerSmartIdSigningStatusResponse response = getRequest(
                "/containers/" + containerId + "/smartidsigning/" + signatureId + "/status",
                GetContainerSmartIdSigningStatusResponse.class
        );
        return response.getSidStatus();
    }

    protected static JSONObject createStartRemoteSigningRequest(String encodedSigningCertificate) {
        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");
        request.put("signingCertificate", encodedSigningCertificate);
        JSONArray roles = new JSONArray();
        roles.put("Manager");
        roles.put("Developer");
        request.put("roles", roles);
        return request;
    }

    private <T> T startRemoteSigning(String url, String encodedSigningCertificate, Class<T> responseObject) throws Exception {
        JSONObject request = createStartRemoteSigningRequest(encodedSigningCertificate);
        return postRequest(url, request, responseObject);
    }

    protected CreateHashcodeContainerRemoteSigningResponse startHashcodeRemoteSigning(String containerId, String encodedSigningCertificate) throws Exception {
        return startRemoteSigning(
                "/hashcodecontainers/" + containerId + "/remotesigning",
                encodedSigningCertificate,
                CreateHashcodeContainerRemoteSigningResponse.class
        );
    }

    protected CreateContainerRemoteSigningResponse startRemoteSigning(String containerId, String encodedSigningCertificate) throws Exception {
        return startRemoteSigning(
                "/containers/" + containerId + "/remotesigning",
                encodedSigningCertificate,
                CreateContainerRemoteSigningResponse.class
        );
    }

    protected static JSONObject createFinalizeRemoteSigningRequest(String signatureValue) {
        JSONObject request = new JSONObject();
        request.put("signatureValue", signatureValue);
        return request;
    }

    protected void finalizeRemoteSigning(String url, String signatureValue) throws Exception {
        JSONObject request = createFinalizeRemoteSigningRequest(signatureValue);
        putRequest(url, request);
    }

    protected static JSONObject createStartMobileSigningRequest() {
        JSONObject request = new JSONObject();
        request.put("personIdentifier", "60001019906");
        request.put("phoneNo", "+37200000766");
        request.put("country", "EE");
        request.put("language", "EST");
        request.put("signatureProfile", "LT");
        return request;
    }

    protected static JSONObject createStartSmartIdSigningRequest(String documentNumber) {
        JSONObject request = new JSONObject();
        request.put("documentNumber", Objects.requireNonNullElse(documentNumber, "PNOEE-40504040001-DEMO-Q"));
        request.put("signatureProfile", "LT");
        return request;
    }

    protected static JSONObject createStartSmartIdCertificateChoiceRequest() {
        JSONObject request = new JSONObject();
        request.put("personIdentifier", "40504040001");
        request.put("country", "EE");
        return request;
    }

    protected String startHashcodeSmartIdCertificateChoice(String containerId) throws Exception {
        CreateHashcodeContainerSmartIdCertificateChoiceResponse response = postRequest(
                "/hashcodecontainers/" + containerId + "/smartidsigning/certificatechoice",
                createStartSmartIdCertificateChoiceRequest(),
                CreateHashcodeContainerSmartIdCertificateChoiceResponse.class
        );
        return response.getGeneratedCertificateId();
    }

    protected String startSmartIdCertificateChoice(String containerId) throws Exception {
        CreateContainerSmartIdCertificateChoiceResponse response = postRequest(
                "/containers/" + containerId + "/smartidsigning/certificatechoice",
                createStartSmartIdCertificateChoiceRequest(),
                CreateContainerSmartIdCertificateChoiceResponse.class
        );
        return response.getGeneratedCertificateId();
    }

    protected String startHashcodeSmartIdSigning(String containerId, String documentNumber) throws Exception {
        CreateHashcodeContainerSmartIdSigningResponse response = postRequest(
                "/hashcodecontainers/" + containerId + "/smartidsigning",
                createStartSmartIdSigningRequest(documentNumber),
                CreateHashcodeContainerSmartIdSigningResponse.class
        );
        return response.getGeneratedSignatureId();
    }

    protected String startSmartIdSigning(String containerId, String documentNumber) throws Exception {
        CreateContainerSmartIdSigningResponse response = postRequest(
                "/containers/" + containerId + "/smartidsigning",
                createStartSmartIdSigningRequest(documentNumber),
                CreateContainerSmartIdSigningResponse.class
        );
        return response.getGeneratedSignatureId();
    }

    protected String startHashcodeMobileSigning(String containerId) throws Exception {
        CreateHashcodeContainerMobileIdSigningResponse response = postRequest(
                "/hashcodecontainers/" + containerId + "/mobileidsigning",
                createStartMobileSigningRequest(),
                CreateHashcodeContainerMobileIdSigningResponse.class
        );
        return response.getGeneratedSignatureId();
    }

    protected String startMobileSigning(String containerId) throws Exception {
        CreateContainerMobileIdSigningResponse response = postRequest(
                "/containers/" + containerId + "/mobileidsigning",
                createStartMobileSigningRequest(),
                CreateContainerMobileIdSigningResponse.class
        );
        return response.getGeneratedSignatureId();
    }

    protected String uploadHashcodeContainer(String containerName) throws Exception {
        String container = IOUtils.toString(getFileInputStream(containerName), Charset.defaultCharset());
        UploadHashcodeContainerResponse containerResponse = uploadContainer(
                container,
                "/upload/hashcodecontainers",
                null,
                UploadHashcodeContainerResponse.class
        );
        return containerResponse.getContainerId();
    }

    protected ValidationConclusion getValidationConclusionByUploadingContainer(String containerName) throws Exception {
        String container = IOUtils.toString(getFileInputStream(containerName), Charset.defaultCharset());
        CreateHashcodeContainerValidationReportResponse containerResponse = uploadContainer(
                container,
                "/hashcodecontainers/validationreport",
                null,
                CreateHashcodeContainerValidationReportResponse.class
        );
        return containerResponse.getValidationConclusion();
    }

    protected String uploadHashcodeContainer() throws Exception {
        return uploadHashcodeContainer("hashcode.asice");
    }

    protected String uploadContainer() throws Exception {
        String container = IOUtils.toString(getFileInputStream("datafile.asice"), Charset.defaultCharset());
        UploadContainerResponse containerResponse = uploadContainer(
                container,
                "/upload/containers",
                "datafile.asice",
                UploadContainerResponse.class
        );
        return containerResponse.getContainerId();
    }

    protected void augmentContainer(String containerId) throws Exception {
        putRequest(
                "/containers/" + containerId + "/augmentation",
                new JSONObject()
        );
    }

    protected static JSONObject createUploadContainerRequest(String container, String containerName) {
        JSONObject request = new JSONObject();
        if (containerName != null) {
            request.put("containerName", containerName);
        }
        request.put("container", container);
        return request;
    }

    private <T> T uploadContainer(String container, String url, String containerName, Class<T> responseObject) throws Exception {
        JSONObject request = createUploadContainerRequest(container, containerName);
        return postRequest(url, request, responseObject);
    }

    private String createHashcodeContainer(String sha256Hash, String sha512Hash) throws Exception {
        JSONObject request = new JSONObject();
        JSONObject dataFile = new JSONObject();
        JSONArray dataFiles = new JSONArray();
        dataFile.put("fileName", "test.txt");
        if (sha256Hash != null) {
            dataFile.put("fileHashSha256", sha256Hash);
        }
        if (sha512Hash != null) {
            dataFile.put("fileHashSha512", sha512Hash);
        }
        dataFile.put("fileSize", 10);
        dataFiles.put(dataFile);
        request.put("dataFiles", dataFiles);
        CreateHashcodeContainerResponse response = (CreateHashcodeContainerResponse) postRequest("/hashcodecontainers", request, CreateHashcodeContainerResponse.class);
        return response.getContainerId();
    }

    protected String createHashcodeContainerWithBothHashes() throws Exception {
        return createHashcodeContainer(
                "K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=",
                "vSsar3708Jvp9Szi2NWZZ02Bqp1qRCFpbcTZPdBhnWgs5WtNZKnvCXdhztmeD2cmW192CF5bDufKRpayrW/isg=="
        );
    }

    protected String createHashcodeContainerWithSha256() throws Exception {
        return createHashcodeContainer(
                "K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=",
                null
        );

    }

    protected static JSONObject createCreateContainerRequest() {
        JSONObject request = new JSONObject();
        JSONObject dataFile = new JSONObject();
        JSONArray dataFiles = new JSONArray();
        request.put("containerName", "test.asice");
        dataFile.put("fileName", "test.txt");
        dataFile.put("fileContent", "cmFuZG9tIHRleHQ=");
        dataFiles.put(dataFile);
        request.put("dataFiles", dataFiles);
        return request;
    }

    protected String createContainer() throws Exception {
        CreateContainerResponse response = postRequest(
                "/containers",
                createCreateContainerRequest(),
                CreateContainerResponse.class
        );
        return response.getContainerId();
    }


    protected void addDataFile(String containerId) throws Exception {
        JSONObject request = new JSONObject();
        JSONObject dataFile = new JSONObject();
        JSONArray dataFiles = new JSONArray();
        dataFile.put("fileName", "test1.txt");
        dataFile.put("fileContent", "cmFuZG9tIHRleHQ=");
        dataFiles.put(dataFile);
        request.put("dataFiles", dataFiles);
        postRequest("/containers/" + containerId + "/datafiles", request, CreateContainerDataFileResponse.class);
    }

    protected void addHashcodeDataFile(String containerId) throws Exception {
        JSONObject request = new JSONObject();
        JSONObject dataFile = new JSONObject();
        JSONArray dataFiles = new JSONArray();
        dataFile.put("fileName", "test1.txt");
        dataFile.put("fileHashSha256", "WxFhjC5EAnh30M0JIe0Wa58Xb1BYf8kedTTdKUbbd9Y=");
        dataFile.put("fileHashSha512", "HD6Xh+Y6oIZnXv4XqbKxrb6t3RkoPYv+NkqOBE8MwkssuATRE2aFBp8Nm9kp/Xn5a4l2Ki8QkX5qIUlbXQgO4Q==");
        dataFile.put("fileSize", 10);
        dataFiles.put(dataFile);
        request.put("dataFiles", dataFiles);
        postRequest("/hashcodecontainers/" + containerId + "/datafiles", request, CreateHashcodeContainerDataFileResponse.class);
    }

    protected void deleteDataFile(String containerId, String dataFileName) throws Exception {
        deleteRequest("/containers/" + containerId + "/datafiles/" + dataFileName);
    }

    protected void deleteHashcodeDataFile(String containerId, String dataFileName) throws Exception {
        deleteRequest("/hashcodecontainers/" + containerId + "/datafiles/" + dataFileName);
    }

    private void deleteRequest(String url) throws Exception {
        JSONObject request = new JSONObject();
        String signature = getSignature("DELETE", url, request.toString());
        MockHttpServletRequestBuilder builder = delete(url);
        mockMvc.perform(buildRequest(builder, signature, request, getServiceUuid()))
                .andExpect(status().is2xxSuccessful());
    }

    private <T> T postRequest(String url, JSONObject request, Class<T> responseObject) throws Exception {
        ResultActions response = postRequest(url, request, status().is2xxSuccessful());
        return objectMapper.readValue(response.andReturn().getResponse().getContentAsString(), responseObject);
    }

    protected ResultActions postRequest(String url, JSONObject request, ResultMatcher resultMatcher) throws Exception {
        String signature = getSignature("POST", url, request.toString());
        MockHttpServletRequestBuilder builder = post(url);

        return mockMvc.perform(buildRequest(builder, signature, request, getServiceUuid())).andExpect(resultMatcher);
    }

    private void putRequest(String url, JSONObject request) throws Exception {
        putRequest(url, request, status().is2xxSuccessful());
    }

    protected ResultActions putRequest(String url, JSONObject request, ResultMatcher resultMatcher) throws Exception {
        String signature = getSignature("PUT", url, request.toString());
        MockHttpServletRequestBuilder builder = put(url);

        return mockMvc.perform(buildRequest(builder, signature, request, getServiceUuid())).andExpect(resultMatcher);
    }

    private <T> T getRequest(String url, Class<T> responseObject) throws Exception {
        ResultActions response = getRequest(url, status().is2xxSuccessful());
        return objectMapper.readValue(response.andReturn().getResponse().getContentAsString(), responseObject);
    }

    protected ResultActions getRequest(String url, ResultMatcher resultMatcher) throws Exception {
        JSONObject request = new JSONObject();
        String signature = getSignature("GET", url, request.toString());
        MockHttpServletRequestBuilder builder = get(url);
        return mockMvc.perform(buildRequest(builder, signature, request, getServiceUuid())).andExpect(resultMatcher);
    }

    protected MockHttpServletRequestBuilder buildRequest(MockHttpServletRequestBuilder builder, String signature, JSONObject request, String serviceUUID) {
        return builder.accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .header(X_AUTHORIZATION_SERVICE_UUID.getValue(), serviceUUID)
                .header(X_AUTHORIZATION_TIMESTAMP.getValue(), xAuthorizationTimestamp)
                .header(X_AUTHORIZATION_SIGNATURE.getValue(), signature)
                .content(request.toString().getBytes());
    }

    protected Callable<Boolean> isHashcodeMobileIdResponseSuccessful(String containerId, String signatureId) {
        return () -> {
            String mobileStatus = getHashcodeMobileIdStatus(containerId, signatureId);
            return SIGNATURE.equals(mobileStatus);
        };
    }

    protected Callable<Boolean> isMobileIdResponseSuccessful(String containerId, String signatureId) {
        return () -> {
            String mobileStatus = getMobileIdStatus(containerId, signatureId);
            return SIGNATURE.equals(mobileStatus);
        };
    }

    protected Callable<Boolean> isSmartIdCertificateChoiceSuccessful(AtomicReference<CertificateStatus> resultHolder, String containerId, String certificateId) {
        return () -> {
            CertificateStatus certificateStatus = getCertificateChoiceStatus(containerId, certificateId);
            if (resultHolder != null) {
                resultHolder.set(certificateStatus);
            }
            return CERTIFICATE.equals(certificateStatus.getStatus());
        };
    }

    protected Callable<Boolean> isHashcodeSmartIdCertificateChoiceSuccessful(AtomicReference<CertificateStatus> resultHolder, String containerId, String certificateId) {
        return () -> {
            CertificateStatus certificateStatus = getHashcodeCertificateChoiceStatus(containerId, certificateId);
            if (resultHolder != null) {
                resultHolder.set(certificateStatus);
            }
            return CERTIFICATE.equals(certificateStatus.getStatus());
        };
    }

    protected Callable<Boolean> isSmartIdResponseSuccessful(String containerId, String signatureId) {
        return () -> {
            String smartIdStatus = getSmartIdStatus(containerId, signatureId);
            return SIGNATURE.equals(smartIdStatus);
        };
    }

    protected Callable<Boolean> isHashcodeSmartIdResponseSuccessful(String containerId, String signatureId) {
        return () -> {
            String smartIdStatus = getHashcodeSmartIdStatus(containerId, signatureId);
            return SIGNATURE.equals(smartIdStatus);
        };
    }

    private String getSignature(String requestMethod, String uri, String payload) throws Exception {
        return HmacSignature.builder()
                .macAlgorithm(DEFAULT_HMAC_ALGO)
                .serviceUuid(getServiceUuid())
                .timestamp(xAuthorizationTimestamp)
                .requestMethod(requestMethod)
                .uri(uri)
                .payload(payload.getBytes())
                .build().getSignature(getHmacSharedSecret());
    }

    protected static InputStream getFileInputStream(String name) throws IOException {
        Path documentPath = Paths.get(new ClassPathResource(name).getURI());
        return new ByteArrayInputStream(Base64.getEncoder().encode(Files.readAllBytes(documentPath)));
    }

    protected String getServiceUuid() {
        return DEFAULT_REQUESTING_SERVICE_UUID;
    }

    protected String getHmacSharedSecret() {
        return DEFAULT_HMAC_SHARED_SECRET;
    }

    protected ServiceType getServiceType() {
        return ServiceType.REST;
    }

}
