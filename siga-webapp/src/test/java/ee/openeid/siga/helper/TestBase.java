package ee.openeid.siga.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.auth.filter.hmac.HmacSignature;
import ee.openeid.siga.common.model.CertificateStatus;
import ee.openeid.siga.common.model.ServiceType;
import ee.openeid.siga.service.signature.hashcode.HashcodeContainer;
import ee.openeid.siga.webapp.json.*;
import org.apache.commons.io.IOUtils;
import org.digidoc4j.Configuration;
import org.digidoc4j.Container;
import org.digidoc4j.ContainerBuilder;
import org.digidoc4j.signers.PKCS12SignatureToken;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
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
import java.util.Objects;
import java.util.concurrent.Callable;

import static ee.openeid.siga.auth.filter.hmac.HmacHeader.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class TestBase {

    protected final static String DEFAULT_HMAC_ALGO = "HmacSHA256";
    private final static String DEFAULT_HMAC_SHARED_SECRET = "746573745365637265744b6579303031";
    private final static String DEFAULT_REQUESTING_SERVICE_UUID = "a7fd7728-a3ea-4975-bfab-f240a67e894f";
    protected final PKCS12SignatureToken pkcs12Esteid2018SignatureToken = new PKCS12SignatureToken("src/test/resources/sign_ESTEID2018.p12", "1234".toCharArray());
    protected String xAuthorizationTimestamp;

    protected void assertHashcodeSignedContainer(String containerId, int validSignatureCount) throws Exception {
        HashcodeContainer container = getHashcodeContainer(containerId);
        Assert.assertEquals(2, container.getSignatures().size());
        Assert.assertEquals(2, container.getDataFiles().size());

        List<Signature> signatures = getHashcodeSignatures(containerId);

        Assert.assertEquals(2, signatures.size());
        ValidationConclusion validationConclusion = getHashcodeValidationConclusion(containerId);
        Assert.assertEquals(Integer.valueOf(validSignatureCount), validationConclusion.getValidSignaturesCount());
        Assert.assertEquals(Integer.valueOf(2), validationConclusion.getSignaturesCount());
    }

    protected void assertSignedContainer(String containerId, int validSignatureCount) throws Exception {
        Container container = getContainer(containerId);
        Assert.assertEquals(2, container.getSignatures().size());
        Assert.assertEquals(2, container.getDataFiles().size());
        List<Signature> signatures = getSignatures(containerId);

        Assert.assertEquals(2, signatures.size());
        ValidationConclusion validationConclusion = getValidationConclusion(containerId);
        Assert.assertEquals(Integer.valueOf(validSignatureCount), validationConclusion.getValidSignaturesCount());
        Assert.assertEquals(Integer.valueOf(2), validationConclusion.getSignaturesCount());
    }

    protected List<HashcodeDataFile> getHashcodeDataFiles(String containerId) throws Exception {
        GetHashcodeContainerDataFilesResponse signaturesResponse = getRequest("/hashcodecontainers/" + containerId + "/datafiles", GetHashcodeContainerDataFilesResponse.class);
        return signaturesResponse.getDataFiles();
    }

    protected List<DataFile> getDataFiles(String containerId) throws Exception {
        GetContainerDataFilesResponse signaturesResponse = getRequest("/containers/" + containerId + "/datafiles", GetContainerDataFilesResponse.class);
        return signaturesResponse.getDataFiles();
    }

    protected List<Signature> getHashcodeSignatures(String containerId) throws Exception {
        GetHashcodeContainerSignaturesResponse signaturesResponse = getRequest("/hashcodecontainers/" + containerId + "/signatures", GetHashcodeContainerSignaturesResponse.class);
        return signaturesResponse.getSignatures();
    }

    protected List<Signature> getSignatures(String containerId) throws Exception {
        GetContainerSignaturesResponse signaturesResponse = getRequest("/containers/" + containerId + "/signatures", GetContainerSignaturesResponse.class);
        return signaturesResponse.getSignatures();
    }

    protected GetContainerSignatureDetailsResponse getSignature(String containerId, String signatureId) throws Exception {
        return getRequest("/containers/" + containerId + "/signatures/" + signatureId, GetContainerSignatureDetailsResponse.class);
    }

    protected GetContainerSignatureDetailsResponse getHashcodeSignature(String containerId, String signatureId) throws Exception {
        return getRequest("/hashcodecontainers/" + containerId + "/signatures/" + signatureId, GetContainerSignatureDetailsResponse.class);
    }

    protected ValidationConclusion getHashcodeValidationConclusion(String containerId) throws Exception {
        GetHashcodeContainerValidationReportResponse response = getRequest("/hashcodecontainers/" + containerId + "/validationreport", GetHashcodeContainerValidationReportResponse.class);
        return response.getValidationConclusion();
    }

    private ValidationConclusion getValidationConclusion(String containerId) throws Exception {
        GetContainerValidationReportResponse response = getRequest("/containers/" + containerId + "/validationreport", GetContainerValidationReportResponse.class);
        return response.getValidationConclusion();
    }

    protected HashcodeContainer getHashcodeContainer(String containerId) throws Exception {
        GetHashcodeContainerResponse originalContainer = getRequest("/hashcodecontainers/" + containerId, GetHashcodeContainerResponse.class);

        HashcodeContainer hashcodeContainer = new HashcodeContainer(getServiceType());
        hashcodeContainer.open(Base64.getDecoder().decode(originalContainer.getContainer()));
        return hashcodeContainer;
    }

    protected Container getContainer(String containerId) throws Exception {
        GetContainerResponse originalContainer = getRequest("/containers/" + containerId, GetContainerResponse.class);
        return ContainerBuilder.aContainer().withConfiguration(Configuration.of(Configuration.Mode.TEST)).fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(originalContainer.getContainer().getBytes()))).build();
    }

    protected String getHashcodeMobileIdStatus(String containerId, String signatureId) throws Exception {
        GetHashcodeContainerMobileIdSigningStatusResponse response = getRequest("/hashcodecontainers/" + containerId + "/mobileidsigning/" + signatureId + "/status", GetHashcodeContainerMobileIdSigningStatusResponse.class);
        return response.getMidStatus();
    }

    protected String getMobileIdStatus(String containerId, String signatureId) throws Exception {
        GetContainerMobileIdSigningStatusResponse response = getRequest("/containers/" + containerId + "/mobileidsigning/" + signatureId + "/status", GetContainerMobileIdSigningStatusResponse.class);
        return response.getMidStatus();
    }

    protected CertificateStatus getHashcodeCertificateChoiceStatus(String containerId, String certificateId) throws Exception {
        GetHashcodeContainerSmartIdCertificateChoiceStatusResponse response = getRequest("/hashcodecontainers/" + containerId + "/smartidsigning/certificatechoice/" + certificateId + "/status", GetHashcodeContainerSmartIdCertificateChoiceStatusResponse.class);
        CertificateStatus status = new CertificateStatus();
        status.setStatus(response.getSidStatus());
        status.setDocumentNumber(response.getDocumentNumber());
        return status;
    }

    protected CertificateStatus getCertificateChoiceStatus(String containerId, String certificateId) throws Exception {
        GetContainerSmartIdCertificateChoiceStatusResponse response = getRequest("/containers/" + containerId + "/smartidsigning/certificatechoice/" + certificateId + "/status", GetContainerSmartIdCertificateChoiceStatusResponse.class);
        CertificateStatus status = new CertificateStatus();
        status.setStatus(response.getSidStatus());
        status.setDocumentNumber(response.getDocumentNumber());
        return status;
    }

    protected String getHashcodeSmartIdStatus(String containerId, String signatureId) throws Exception {
        GetHashcodeContainerSmartIdSigningStatusResponse response = getRequest("/hashcodecontainers/" + containerId + "/smartidsigning/" + signatureId + "/status", GetHashcodeContainerSmartIdSigningStatusResponse.class);
        return response.getSidStatus();
    }

    protected String getSmartIdStatus(String containerId, String signatureId) throws Exception {
        GetContainerSmartIdSigningStatusResponse response = getRequest("/containers/" + containerId + "/smartidsigning/" + signatureId + "/status", GetContainerSmartIdSigningStatusResponse.class);
        return response.getSidStatus();
    }

    private <T> T startRemoteSigning(String url, String encodedSigningCertificate, Class<T> responseObject) throws Exception {
        JSONObject request = new JSONObject();
        request.put("signatureProfile", "LT");
        request.put("signingCertificate", encodedSigningCertificate);
        JSONArray roles = new JSONArray();
        roles.put("Manager");
        roles.put("Developer");
        request.put("roles", roles);
        return postRequest(url, request, responseObject);
    }

    protected CreateHashcodeContainerRemoteSigningResponse startHashcodeRemoteSigning(String containerId, String encodedSigningCertificate) throws Exception {
        return startRemoteSigning("/hashcodecontainers/" + containerId + "/remotesigning", encodedSigningCertificate, CreateHashcodeContainerRemoteSigningResponse.class);
    }

    protected CreateContainerRemoteSigningResponse startRemoteSigning(String containerId, String encodedSigningCertificate) throws Exception {
        return startRemoteSigning("/containers/" + containerId + "/remotesigning", encodedSigningCertificate, CreateContainerRemoteSigningResponse.class);
    }

    protected void finalizeRemoteSigning(String url, String signatureValue) throws Exception {
        JSONObject request = new JSONObject();
        request.put("signatureValue", signatureValue);
        putRequest(url, request);
    }

    private <T> T startMobileSigning(String url, Class<T> responseObject) throws Exception {
        JSONObject request = new JSONObject();
        request.put("personIdentifier", "60001019906");
        request.put("phoneNo", "+37200000766");
        request.put("country", "EE");
        request.put("language", "EST");
        request.put("signatureProfile", "LT");
        return postRequest(url, request, responseObject);
    }

    private <T> T startSmartIdSigning(String url, String documentNumber, Class<T> responseObject) throws Exception {
        JSONObject request = new JSONObject();
        request.put("documentNumber", Objects.requireNonNullElse(documentNumber, "PNOEE-30303039914-D961-Q"));
        request.put("signatureProfile", "LT");
        return postRequest(url, request, responseObject);
    }

    private <T> T startSmartIdCertificateChoice(String url, Class<T> responseObject) throws Exception {
        JSONObject request = new JSONObject();
        request.put("personIdentifier", "30303039914");
        request.put("country", "EE");
        return postRequest(url, request, responseObject);
    }

    protected String startHashcodeSmartIdCertificateChoice(String containerId) throws Exception {
        CreateHashcodeContainerSmartIdCertificateChoiceResponse response = startSmartIdCertificateChoice("/hashcodecontainers/" + containerId + "/smartidsigning/certificatechoice", CreateHashcodeContainerSmartIdCertificateChoiceResponse.class);
        return response.getGeneratedCertificateId();
    }

    protected String startSmartIdCertificateChoice(String containerId) throws Exception {
        CreateContainerSmartIdCertificateChoiceResponse response = startSmartIdCertificateChoice("/containers/" + containerId + "/smartidsigning/certificatechoice", CreateContainerSmartIdCertificateChoiceResponse.class);
        return response.getGeneratedCertificateId();
    }

    protected String startHashcodeSmartIdSigning(String containerId, String documentNumber) throws Exception {
        CreateHashcodeContainerSmartIdSigningResponse response = startSmartIdSigning("/hashcodecontainers/" + containerId + "/smartidsigning", documentNumber, CreateHashcodeContainerSmartIdSigningResponse.class);
        return response.getGeneratedSignatureId();
    }

    protected String startSmartIdSigning(String containerId, String documentNumber) throws Exception {
        CreateContainerSmartIdSigningResponse response = startSmartIdSigning("/containers/" + containerId + "/smartidsigning", documentNumber, CreateContainerSmartIdSigningResponse.class);
        return response.getGeneratedSignatureId();
    }


    protected String startHashcodeMobileSigning(String containerId) throws Exception {
        CreateHashcodeContainerMobileIdSigningResponse response = startMobileSigning("/hashcodecontainers/" + containerId + "/mobileidsigning", CreateHashcodeContainerMobileIdSigningResponse.class);
        return response.getGeneratedSignatureId();
    }

    protected String startMobileSigning(String containerId) throws Exception {
        CreateContainerMobileIdSigningResponse response = startMobileSigning("/containers/" + containerId + "/mobileidsigning", CreateContainerMobileIdSigningResponse.class);
        return response.getGeneratedSignatureId();
    }

    protected String uploadHashcodeContainer(String containerName) throws Exception {
        String container = IOUtils.toString(getFileInputStream(containerName), Charset.defaultCharset());
        UploadHashcodeContainerResponse containerResponse = uploadContainer(container, "/upload/hashcodecontainers", null, UploadHashcodeContainerResponse.class);
        return containerResponse.getContainerId();
    }

    protected ValidationConclusion getValidationConclusionByUploadingContainer(String containerName) throws Exception {
        String container = IOUtils.toString(getFileInputStream(containerName), Charset.defaultCharset());
        CreateHashcodeContainerValidationReportResponse containerResponse = uploadContainer(container, "/hashcodecontainers/validationreport", null, CreateHashcodeContainerValidationReportResponse.class);
        return containerResponse.getValidationConclusion();
    }

    protected String uploadHashcodeContainer() throws Exception {
        return uploadHashcodeContainer("hashcode.asice");
    }

    protected String uploadContainer() throws Exception {
        String container = IOUtils.toString(getFileInputStream("datafile.asice"), Charset.defaultCharset());
        UploadContainerResponse containerResponse = uploadContainer(container, "/upload/containers", "datafile.asice", UploadContainerResponse.class);
        return containerResponse.getContainerId();
    }

    private <T> T uploadContainer(String container, String url, String containerName, Class<T> responseObject) throws Exception {
        JSONObject request = new JSONObject();
        if (containerName != null) {
            request.put("containerName", containerName);
        }
        request.put("container", container);
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
        return createHashcodeContainer("K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=", "vSsar3708Jvp9Szi2NWZZ02Bqp1qRCFpbcTZPdBhnWgs5WtNZKnvCXdhztmeD2cmW192CF5bDufKRpayrW/isg==");
    }

    protected String createHashcodeContainerWithSha256() throws Exception {
        return createHashcodeContainer("K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=", null);

    }

    protected String createContainer() throws Exception {
        JSONObject request = new JSONObject();
        JSONObject dataFile = new JSONObject();
        JSONArray dataFiles = new JSONArray();
        request.put("containerName", "test.asice");
        dataFile.put("fileName", "test.txt");
        dataFile.put("fileContent", "cmFuZG9tIHRleHQ=");
        dataFiles.put(dataFile);
        request.put("dataFiles", dataFiles);
        CreateContainerResponse response = postRequest("/containers", request, CreateContainerResponse.class);
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
        getMockMvc().perform(buildRequest(builder, signature, request, getServiceUuid()))
                .andExpect(status().is2xxSuccessful());
    }

    private <T> T postRequest(String url, JSONObject request, Class<T> responseObject) throws Exception {
        String signature = getSignature("POST", url, request.toString());
        MockHttpServletRequestBuilder builder = post(url);

        ResultActions response = getMockMvc().perform(buildRequest(builder, signature, request, getServiceUuid()))
                .andExpect(status().is2xxSuccessful());
        return getObjectMapper().readValue(response.andReturn().getResponse().getContentAsString(), responseObject);
    }

    private void putRequest(String url, JSONObject request) throws Exception {
        String signature = getSignature("PUT", url, request.toString());
        MockHttpServletRequestBuilder builder = put(url);

        getMockMvc().perform(buildRequest(builder, signature, request, getServiceUuid()))
                .andExpect(status().is2xxSuccessful());
    }

    private <T> T getRequest(String url, Class<T> responseObject) throws Exception {
        JSONObject request = new JSONObject();
        String signature = getSignature("GET", url, request.toString());
        MockHttpServletRequestBuilder builder = get(url);
        ResultActions response = getMockMvc().perform(buildRequest(builder, signature, request, getServiceUuid()))
                .andExpect(status().is2xxSuccessful());
        return getObjectMapper().readValue(response.andReturn().getResponse().getContentAsString(), responseObject);
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
            return "SIGNATURE".equals(mobileStatus);
        };
    }

    protected Callable<Boolean> isMobileIdResponseSuccessful(String containerId, String signatureId) {
        return () -> {
            String mobileStatus = getMobileIdStatus(containerId, signatureId);
            return "SIGNATURE".equals(mobileStatus);
        };
    }

    protected Callable<Boolean> isSmartIdResponseSuccessful(String containerId, String signatureId) {
        return () -> {
            String smartIdStatus = getSmartIdStatus(containerId, signatureId);
            return "SIGNATURE".equals(smartIdStatus);
        };
    }

    protected Callable<Boolean> isHashcodeSmartIdResponseSuccessful(String containerId, String signatureId) {
        return () -> {
            String smartIdStatus = getHashcodeSmartIdStatus(containerId, signatureId);
            return "SIGNATURE".equals(smartIdStatus);
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

    private InputStream getFileInputStream(String name) throws IOException {
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

    protected abstract ObjectMapper getObjectMapper();

    protected abstract MockMvc getMockMvc();
}
