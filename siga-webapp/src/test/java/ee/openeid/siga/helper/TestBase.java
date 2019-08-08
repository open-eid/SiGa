package ee.openeid.siga.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.openeid.siga.auth.filter.hmac.HmacSignature;
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

import static ee.openeid.siga.auth.filter.hmac.HmacHeader.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class TestBase {

    protected final static String DEFAULT_HMAC_ALGO = "HmacSHA256";
    private final static String HMAC_SHARED_SECRET = "746573745365637265744b6579303031";
    private final static String REQUESTING_SERVICE_UUID = "a7fd7728-a3ea-4975-bfab-f240a67e894f";
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
        GetHashcodeContainerDataFilesResponse signaturesResponse = (GetHashcodeContainerDataFilesResponse) getRequest("/hashcodecontainers/" + containerId + "/datafiles", GetHashcodeContainerDataFilesResponse.class);
        return signaturesResponse.getDataFiles();
    }

    protected List<DataFile> getDataFiles(String containerId) throws Exception {
        GetContainerDataFilesResponse signaturesResponse = (GetContainerDataFilesResponse) getRequest("/containers/" + containerId + "/datafiles", GetContainerDataFilesResponse.class);
        return signaturesResponse.getDataFiles();
    }

    protected List<Signature> getHashcodeSignatures(String containerId) throws Exception {
        GetHashcodeContainerSignaturesResponse signaturesResponse = (GetHashcodeContainerSignaturesResponse) getRequest("/hashcodecontainers/" + containerId + "/signatures", GetHashcodeContainerSignaturesResponse.class);
        return signaturesResponse.getSignatures();
    }

    protected List<Signature> getSignatures(String containerId) throws Exception {
        GetContainerSignaturesResponse signaturesResponse = (GetContainerSignaturesResponse) getRequest("/containers/" + containerId + "/signatures", GetContainerSignaturesResponse.class);
        return signaturesResponse.getSignatures();
    }

    protected GetContainerSignatureDetailsResponse getSignature(String containerId, String signatureId) throws Exception {
        return (GetContainerSignatureDetailsResponse) getRequest("/containers/" + containerId + "/signatures/" + signatureId, GetContainerSignatureDetailsResponse.class);
    }

    protected GetHashcodeContainerSignatureDetailsResponse getHashcodeSignature(String containerId, String signatureId) throws Exception {
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

    protected HashcodeContainer getHashcodeContainer(String containerId) throws Exception {
        GetHashcodeContainerResponse originalContainer = (GetHashcodeContainerResponse) getRequest("/hashcodecontainers/" + containerId, GetHashcodeContainerResponse.class);

        HashcodeContainer hashcodeContainer = new HashcodeContainer();
        hashcodeContainer.open(new ByteArrayInputStream(Base64.getDecoder().decode(originalContainer.getContainer())));
        return hashcodeContainer;
    }

    protected Container getContainer(String containerId) throws Exception {
        GetContainerResponse originalContainer = (GetContainerResponse) getRequest("/containers/" + containerId, GetContainerResponse.class);
        return ContainerBuilder.aContainer().withConfiguration(Configuration.of(Configuration.Mode.TEST)).fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(originalContainer.getContainer().getBytes()))).build();
    }

    protected String getHashcodeMobileIdStatus(String containerId, String signatureId) throws Exception {
        GetHashcodeContainerMobileIdSigningStatusResponse response = (GetHashcodeContainerMobileIdSigningStatusResponse) getRequest("/hashcodecontainers/" + containerId + "/mobileidsigning/" + signatureId + "/status", GetHashcodeContainerMobileIdSigningStatusResponse.class);
        return response.getMidStatus();
    }

    protected String getMobileIdStatus(String containerId, String signatureId) throws Exception {
        GetContainerMobileIdSigningStatusResponse response = (GetContainerMobileIdSigningStatusResponse) getRequest("/containers/" + containerId + "/mobileidsigning/" + signatureId + "/status", GetContainerMobileIdSigningStatusResponse.class);
        return response.getMidStatus();
    }

    protected String getHashcodeSmartIdStatus(String containerId, String signatureId) throws Exception {
        GetHashcodeContainerSmartIdSigningStatusResponse response = (GetHashcodeContainerSmartIdSigningStatusResponse) getRequest("/hashcodecontainers/" + containerId + "/smartidsigning/" + signatureId + "/status", GetHashcodeContainerSmartIdSigningStatusResponse.class);
        return response.getSidStatus();
    }

    protected String getSmartIdStatus(String containerId, String signatureId) throws Exception {
        GetContainerSmartIdSigningStatusResponse response = (GetContainerSmartIdSigningStatusResponse) getRequest("/containers/" + containerId + "/smartidsigning/" + signatureId + "/status", GetContainerSmartIdSigningStatusResponse.class);
        return response.getSidStatus();
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

    protected CreateHashcodeContainerRemoteSigningResponse startHashcodeRemoteSigning(String containerId) throws Exception {
        return (CreateHashcodeContainerRemoteSigningResponse) startRemoteSigning("/hashcodecontainers/" + containerId + "/remotesigning", CreateHashcodeContainerRemoteSigningResponse.class);
    }

    protected CreateContainerRemoteSigningResponse startRemoteSigning(String containerId) throws Exception {
        return (CreateContainerRemoteSigningResponse) startRemoteSigning("/containers/" + containerId + "/remotesigning", CreateContainerRemoteSigningResponse.class);
    }

    protected void finalizeRemoteSigning(String url, String signatureValue) throws Exception {
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

    private Object startSmartIdSigning(String url, Class responseObject) throws Exception {
        JSONObject request = new JSONObject();
        request.put("personIdentifier", "10101010005");
        request.put("country", "EE");
        request.put("signatureProfile", "LT");
        return postRequest(url, request, responseObject);
    }

    protected String startHashcodeSmartIdSigning(String containerId) throws Exception {
        CreateHashcodeContainerSmartIdSigningResponse response = (CreateHashcodeContainerSmartIdSigningResponse) startSmartIdSigning("/hashcodecontainers/" + containerId + "/smartidsigning", CreateHashcodeContainerSmartIdSigningResponse.class);
        return response.getGeneratedSignatureId();
    }

    protected String startSmartIdSigning(String containerId) throws Exception {
        CreateContainerSmartIdSigningResponse response = (CreateContainerSmartIdSigningResponse) startSmartIdSigning("/containers/" + containerId + "/smartidsigning", CreateContainerSmartIdSigningResponse.class);
        return response.getGeneratedSignatureId();
    }


    protected String startHashcodeMobileSigning(String containerId) throws Exception {
        CreateHashcodeContainerMobileIdSigningResponse response = (CreateHashcodeContainerMobileIdSigningResponse) startMobileSigning("/hashcodecontainers/" + containerId + "/mobileidsigning", CreateHashcodeContainerMobileIdSigningResponse.class);
        return response.getGeneratedSignatureId();
    }

    protected String startMobileSigning(String containerId) throws Exception {
        CreateContainerMobileIdSigningResponse response = (CreateContainerMobileIdSigningResponse) startMobileSigning("/containers/" + containerId + "/mobileidsigning", CreateContainerMobileIdSigningResponse.class);
        return response.getGeneratedSignatureId();
    }

    protected String uploadHashcodeContainer() throws Exception {
        String container = IOUtils.toString(getFileInputStream("hashcode.asice"), Charset.defaultCharset());
        UploadHashcodeContainerResponse containerResponse = (UploadHashcodeContainerResponse) uploadContainer(container, "/upload/hashcodecontainers", null, UploadHashcodeContainerResponse.class);
        return containerResponse.getContainerId();
    }

    protected String uploadContainer() throws Exception {
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

    protected String createHashcodeContainer() throws Exception {
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

    protected String createContainer() throws Exception {
        JSONObject request = new JSONObject();
        JSONObject dataFile = new JSONObject();
        JSONArray dataFiles = new JSONArray();
        request.put("containerName", "test.asice");
        dataFile.put("fileName", "test.txt");
        dataFile.put("fileContent", "cmFuZG9tIHRleHQ=");
        dataFiles.put(dataFile);
        request.put("dataFiles", dataFiles);
        CreateContainerResponse response = (CreateContainerResponse) postRequest("/containers", request, CreateContainerResponse.class);
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
        getMockMvc().perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());
    }

    private Object postRequest(String url, JSONObject request, Class responseObject) throws Exception {
        String signature = getSignature("POST", url, request.toString());
        MockHttpServletRequestBuilder builder = post(url);

        ResultActions response = getMockMvc().perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());
        return getObjectMapper().readValue(response.andReturn().getResponse().getContentAsString(), responseObject);
    }

    private void putRequest(String url, JSONObject request) throws Exception {
        String signature = getSignature("PUT", url, request.toString());
        MockHttpServletRequestBuilder builder = put(url);

        getMockMvc().perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
                .andExpect(status().is2xxSuccessful());
    }

    private Object getRequest(String url, Class responseObject) throws Exception {
        JSONObject request = new JSONObject();
        String signature = getSignature("GET", url, request.toString());
        MockHttpServletRequestBuilder builder = get(url);
        ResultActions response = getMockMvc().perform(buildRequest(builder, signature, request, REQUESTING_SERVICE_UUID))
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
    protected abstract ObjectMapper getObjectMapper();
    protected abstract MockMvc getMockMvc();
}
