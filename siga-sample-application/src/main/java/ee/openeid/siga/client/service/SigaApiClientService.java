package ee.openeid.siga.client.service;

import ee.openeid.siga.client.hashcode.HashcodeContainer;
import ee.openeid.siga.client.hmac.HmacTokenAuthorizationHeaderInterceptor;
import ee.openeid.siga.client.model.GetContainerMobileIdSigningStatusResponse;
import ee.openeid.siga.client.model.*;
import ee.openeid.siga.webapp.json.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.multipart.MultipartFile;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static ee.openeid.siga.client.hashcode.HashcodesDataFileCreator.createHashcodeDataFile;
import static java.text.MessageFormat.format;
import static org.apache.tomcat.util.codec.binary.Base64.encodeBase64String;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.Series.SUCCESSFUL;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

@Slf4j
@Service
@RequestScope
public class SigaApiClientService {

    private static final String ASIC_ENDPOINT = "containers";
    private static final String HASHCODE_ENDPOINT = "hashcodecontainers";
    private static final String RESULT_OK = "OK";
    private static final String SIGNATURE_PROFILE_LT = "LT";

    private final String hmacAlgorithm;
    private final String hmacServiceUuid;
    private final String hmacSharedSigningKey;
    private final String sigaApiUri;
    private RestTemplate restTemplate;
    private String websocketChannelId;

    @Autowired
    private ContainerService containerService;
    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    public SigaApiClientService(@Autowired RestTemplateBuilder restTemplateBuilder,
                                @Value("${siga.client.hmac.algorithm}") String hmacAlgorithm,
                                @Value("${siga.client.hmac.service-uuid}") String hmacServiceUuid,
                                @Value("${siga.client.hmac.shared-signing-key}") String hmacSharedSigningKey,
                                @Value("${siga.api.uri}") String sigaApiUri,
                                @Value("${siga.api.trustStore}") String trustStore,
                                @Value("${siga.api.trustStorePassword}") String trustStorePassword) {
        this.hmacAlgorithm = hmacAlgorithm;
        this.hmacServiceUuid = hmacServiceUuid;
        this.hmacSharedSigningKey = hmacSharedSigningKey;
        this.sigaApiUri = sigaApiUri + "/";
        setUpRestTemplateForRequestScope(restTemplateBuilder, trustStore, trustStorePassword);
    }

    @SneakyThrows
    private void setUpRestTemplateForRequestScope(RestTemplateBuilder restTemplateBuilder, String trustStore, String trustStorePassword) {
        SSLContext sslContext = new SSLContextBuilder()
                .loadTrustMaterial(new ClassPathResource(trustStore).getURL(), trustStorePassword.toCharArray())
                .build();
        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        HttpClient httpClient = HttpClients.custom().setSSLSocketFactory(socketFactory).build();

        restTemplate = restTemplateBuilder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .interceptors(new HmacTokenAuthorizationHeaderInterceptor(sigaApiUri, hmacAlgorithm, hmacServiceUuid, hmacSharedSigningKey))
                .errorHandler(new RestTemplateResponseErrorHandler()).build();
    }

    @Async
    @SneakyThrows
    public void startMobileSigningFlow(MobileSigningRequest mobileSigningRequest) {
        setUpClientNotificationChannel(mobileSigningRequest.getContainerId());

        if (MobileSigningRequest.ContainerType.HASHCODE == mobileSigningRequest.getContainerType()) {
            startHashcodeMobileIdSigningFlow(mobileSigningRequest);
        } else {
            startAsicMobileIdSigningFlow(mobileSigningRequest);
        }
    }

    @Async
    @SneakyThrows
    public void startSmartIdSigningFlow(SmartIdSigningRequest smartIdSigningRequest) {
        setUpClientNotificationChannel(smartIdSigningRequest.getContainerId());

        if (MobileSigningRequest.ContainerType.HASHCODE == smartIdSigningRequest.getContainerType()) {
            startHashcodeSmartIdSigningFlow(smartIdSigningRequest);
        } else {
            throw new Exception("Not Implemented!");
        }
    }

    private void startHashcodeMobileIdSigningFlow(MobileSigningRequest mobileSigningRequest) {
        String containerId = mobileSigningRequest.getContainerId();

        getSignatureList(HASHCODE_ENDPOINT, containerId, GetHashcodeContainerSignaturesResponse.class);
        CreateHashcodeContainerMobileIdSigningRequest mobileIdRequest = createHashcodeMobileIdRequest(mobileSigningRequest);
        CreateHashcodeContainerMobileIdSigningResponse mobileIdResponse =
                prepareMobileIdSignatureSigning(HASHCODE_ENDPOINT, containerId, CreateHashcodeContainerMobileIdSigningResponse.class, mobileIdRequest);

        String generatedSignatureId = mobileIdResponse.getGeneratedSignatureId();
        if (StringUtils.isNotBlank(generatedSignatureId)) {
            if (getMobileSigningStatus(HASHCODE_ENDPOINT, containerId, generatedSignatureId)) {
                endHashcodeContainerFlow(containerId);
            }
        }
    }

    private void endHashcodeContainerFlow(String containerId) {
        getContainerValidation(HASHCODE_ENDPOINT, containerId, GetHashcodeContainerValidationReportResponse.class);
        GetHashcodeContainerResponse getContainerResponse = getContainer(HASHCODE_ENDPOINT, containerId, GetHashcodeContainerResponse.class);
        HashcodeContainerWrapper container = containerService.getHashcodeContainer(containerId);
        containerService.cacheHashcodeContainer(containerId, container.getFileName(), Base64.getDecoder().decode(getContainerResponse.getContainer()), container.getOriginalDataFiles());
        deleteContainer(HASHCODE_ENDPOINT, containerId, DeleteHashcodeContainerResponse.class);
    }

    private void startAsicMobileIdSigningFlow(MobileSigningRequest mobileSigningRequest) {
        String containerId = mobileSigningRequest.getContainerId();

        getSignatureList(ASIC_ENDPOINT, containerId, GetContainerSignaturesResponse.class);
        CreateContainerMobileIdSigningRequest mobileIdRequest = createAsicMobileIdRequest(mobileSigningRequest);
        CreateContainerMobileIdSigningResponse mobileIdResponse =
                prepareMobileIdSignatureSigning(ASIC_ENDPOINT, containerId, CreateContainerMobileIdSigningResponse.class, mobileIdRequest);

        String generatedSignatureId = mobileIdResponse.getGeneratedSignatureId();
        if (StringUtils.isNotBlank(generatedSignatureId)) {
            if (getMobileSigningStatus(ASIC_ENDPOINT, containerId, generatedSignatureId)) {
                endAsicContainerFlow(containerId);
            }
        }
    }

    private void endAsicContainerFlow(String containerId) {
        getContainerValidation(ASIC_ENDPOINT, containerId, GetContainerValidationReportResponse.class);
        GetContainerResponse getContainerResponse = getContainer(ASIC_ENDPOINT, containerId, GetContainerResponse.class);
        AsicContainerWrapper container = containerService.getAsicContainer(containerId);
        containerService.cacheAsicContainer(containerId, container.getName(), Base64.getDecoder().decode(getContainerResponse.getContainer()));
        deleteContainer(ASIC_ENDPOINT, containerId, DeleteContainerResponse.class);
    }

    private void setUpClientNotificationChannel(String fileId) {
        websocketChannelId = "/progress/" + fileId;
    }

    public PrepareRemoteSigningResponse prepareRemoteSigning(PrepareRemoteSigningRequest prepareRemoteSigningRequest) {
        setUpClientNotificationChannel(prepareRemoteSigningRequest.getContainerId());

        if (MobileSigningRequest.ContainerType.HASHCODE == prepareRemoteSigningRequest.getContainerType()) {
            return prepareHashcodeContainerRemoteSigning(prepareRemoteSigningRequest);
        } else {
            return prepareAsicContainerRemoteSigning(prepareRemoteSigningRequest);
        }
    }

    private PrepareRemoteSigningResponse prepareHashcodeContainerRemoteSigning(PrepareRemoteSigningRequest prepareRemoteSigningRequest) {
        String containerId = prepareRemoteSigningRequest.getContainerId();

        getSignatureList(HASHCODE_ENDPOINT, containerId, GetContainerSignaturesResponse.class);

        CreateHashcodeContainerRemoteSigningRequest remoteSigningRequest = createHashcodeContainerRemoteSigningRequest(prepareRemoteSigningRequest);
        CreateHashcodeContainerRemoteSigningResponse remoteSigningResponse = prepareContainerRemoteSigning(HASHCODE_ENDPOINT, containerId, CreateHashcodeContainerRemoteSigningResponse.class, remoteSigningRequest);

        return PrepareRemoteSigningResponse.from(remoteSigningResponse);
    }

    private CreateHashcodeContainerRemoteSigningRequest createHashcodeContainerRemoteSigningRequest(PrepareRemoteSigningRequest prepareRemoteSigningRequest) {
        CreateHashcodeContainerRemoteSigningRequest request = new CreateHashcodeContainerRemoteSigningRequest();
        request.setSigningCertificate(encodeBase64String(prepareRemoteSigningRequest.getCertificate()));
        request.setSignatureProfile(SIGNATURE_PROFILE_LT);
        return request;
    }

    private PrepareRemoteSigningResponse prepareAsicContainerRemoteSigning(PrepareRemoteSigningRequest prepareRemoteSigningRequest) {
        String containerId = prepareRemoteSigningRequest.getContainerId();

        getSignatureList(ASIC_ENDPOINT, containerId, GetContainerSignaturesResponse.class);

        CreateContainerRemoteSigningRequest remoteSigningRequest = createAsicContainerRemoteSigningRequest(prepareRemoteSigningRequest);
        CreateContainerRemoteSigningResponse remoteSigningResponse = prepareContainerRemoteSigning(ASIC_ENDPOINT, containerId, CreateContainerRemoteSigningResponse.class, remoteSigningRequest);

        return PrepareRemoteSigningResponse.from(remoteSigningResponse);
    }

    private CreateContainerRemoteSigningRequest createAsicContainerRemoteSigningRequest(PrepareRemoteSigningRequest prepareRemoteSigningRequest) {
        CreateContainerRemoteSigningRequest request = new CreateContainerRemoteSigningRequest();
        request.setSigningCertificate(encodeBase64String(prepareRemoteSigningRequest.getCertificate()));
        request.setSignatureProfile(SIGNATURE_PROFILE_LT);
        return request;
    }

    private <T> T prepareContainerRemoteSigning(String containerEndpoint, String containerId, Class<T> clazz, Object request) {
        String endpoint = getSigaApiUri(containerEndpoint, containerId, "remotesigning");
        T response = restTemplate.postForObject(endpoint, request, clazz);
        sendStatus(POST, endpoint, request, response);
        return response;
    }

    public void finalizeRemoteSigning(FinalizeRemoteSigningRequest finalizeRemoteSigningRequest) {
        setUpClientNotificationChannel(finalizeRemoteSigningRequest.getContainerId());

        if (MobileSigningRequest.ContainerType.HASHCODE == finalizeRemoteSigningRequest.getContainerType()) {
            finalizeHashcodeContainerRemoteSigning(finalizeRemoteSigningRequest);
        } else {
            finalizeAsicContainerRemoteSigning(finalizeRemoteSigningRequest);
        }
    }

    private void finalizeHashcodeContainerRemoteSigning(FinalizeRemoteSigningRequest finalizeRemoteSigningRequest) {
        String containerId = finalizeRemoteSigningRequest.getContainerId();

        UpdateHashcodeContainerRemoteSigningRequest remoteSigningRequest = new UpdateHashcodeContainerRemoteSigningRequest();
        remoteSigningRequest.setSignatureValue(encodeBase64String(finalizeRemoteSigningRequest.getSignature()));

        HttpEntity<UpdateHashcodeContainerRemoteSigningRequest> request = new HttpEntity<>(remoteSigningRequest);

        UpdateHashcodeContainerRemoteSigningResponse response = finalizeContainerRemoteSignature(HASHCODE_ENDPOINT,
                containerId, finalizeRemoteSigningRequest.getSignatureId(), UpdateHashcodeContainerRemoteSigningResponse.class, request);

        if (RESULT_OK.equals(response.getResult())) {
            endHashcodeContainerFlow(containerId);
        }
    }

    private void finalizeAsicContainerRemoteSigning(FinalizeRemoteSigningRequest finalizeRemoteSigningRequest) {
        String containerId = finalizeRemoteSigningRequest.getContainerId();

        UpdateContainerRemoteSigningRequest remoteSigningRequest = new UpdateContainerRemoteSigningRequest();
        remoteSigningRequest.setSignatureValue(encodeBase64String(finalizeRemoteSigningRequest.getSignature()));

        HttpEntity<UpdateContainerRemoteSigningRequest> request = new HttpEntity<>(remoteSigningRequest);

        UpdateContainerRemoteSigningResponse response = finalizeContainerRemoteSignature(ASIC_ENDPOINT,
                containerId, finalizeRemoteSigningRequest.getSignatureId(), UpdateContainerRemoteSigningResponse.class, request);

        if (RESULT_OK.equals(response.getResult())) {
            endAsicContainerFlow(containerId);
        }
    }

    private <T> T finalizeContainerRemoteSignature(String containerEndpoint, String containerId, String generatedSignatureId, Class<T> clazz, HttpEntity<?> request) {
        String endpoint = getSigaApiUri(containerEndpoint, containerId, "remotesigning", generatedSignatureId);
        T response = restTemplate.exchange(endpoint, PUT, request, clazz).getBody();
        sendStatus(PUT, endpoint, request.getBody(), response);
        return response;
    }

    @SneakyThrows
    public AsicContainerWrapper createAsicContainer(Collection<MultipartFile> files) {
        CreateContainerRequest request = new CreateContainerRequest();
        for (MultipartFile file : files) {
            log.info("Processing file: {}", file.getOriginalFilename());
            ee.openeid.siga.webapp.json.DataFile dataFile = new ee.openeid.siga.webapp.json.DataFile();
            dataFile.setFileContent(new String(Base64.getEncoder().encode(file.getBytes())));
            dataFile.setFileName(file.getOriginalFilename());
            request.getDataFiles().add(dataFile);
        }
        request.setContainerName("container.asice");
        CreateContainerResponse createContainerResponse = restTemplate.postForObject(fromUriString(sigaApiUri).path("containers").build().toUriString(), request, CreateContainerResponse.class);
        String containerId = createContainerResponse.getContainerId();
        GetContainerResponse getContainerResponse = restTemplate.getForObject(getSigaApiUri(ASIC_ENDPOINT, containerId), GetContainerResponse.class);
        return containerService.cacheAsicContainer(containerId, getContainerResponse.getContainerName(), getContainerResponse.getContainer().getBytes());
    }

    @SneakyThrows
    public HashcodeContainerWrapper createHashcodeContainer(Collection<MultipartFile> files) {
        CreateHashcodeContainerRequest request = new CreateHashcodeContainerRequest();
        Map<String, byte[]> originalDataFiles = new HashMap<>();
        for (MultipartFile file : files) {
            log.info("Processing file: {}", file.getOriginalFilename());
            request.getDataFiles().add(createHashcodeDataFile(file.getOriginalFilename(), file.getSize(), file.getBytes()).convertToRequest());
            originalDataFiles.put(file.getOriginalFilename(), file.getBytes());
        }
        CreateHashcodeContainerResponse createContainerResponse = restTemplate.postForObject(fromUriString(sigaApiUri).path("hashcodecontainers").build().toUriString(), request, CreateHashcodeContainerResponse.class);
        String containerId = createContainerResponse.getContainerId();
        GetHashcodeContainerResponse getContainerResponse = restTemplate.getForObject(getSigaApiUri(HASHCODE_ENDPOINT, containerId), GetHashcodeContainerResponse.class);
        log.info("Created container with id {}", containerId);
        return containerService.cacheHashcodeContainer(containerId, containerId + ".asice", Base64.getDecoder().decode(getContainerResponse.getContainer()), originalDataFiles);
    }

    @SneakyThrows
    public HashcodeContainerWrapper convertAndUploadHashcodeContainer(Map<String, MultipartFile> fileMap) {
        HashcodeContainer hashcodeContainer = convertToHashcodeContainer(fileMap);
        UploadHashcodeContainerResponse response = uploadHashcodeContainer(hashcodeContainer);

        String containerId = response.getContainerId();
        GetHashcodeContainerResponse getContainerResponse = restTemplate.getForObject(getSigaApiUri(HASHCODE_ENDPOINT, containerId), GetHashcodeContainerResponse.class);
        log.info("Uploaded hashcode container with id {}", containerId);
        return containerService.cacheHashcodeContainer(containerId, containerId + ".asice", Base64.getDecoder().decode(getContainerResponse.getContainer()), hashcodeContainer.getRegularDataFiles());
    }

    private HashcodeContainer convertToHashcodeContainer(Map<String, MultipartFile> fileMap) throws IOException {
        MultipartFile file = fileMap.entrySet().iterator().next().getValue();
        log.info("Converting container: {}", file.getOriginalFilename());
        return HashcodeContainer.fromRegularContainerBuilder()
                .container(file.getBytes())
                .build();
    }

    private UploadHashcodeContainerResponse uploadHashcodeContainer(HashcodeContainer hashcodeContainer) {
        String endpoint = fromUriString(sigaApiUri).path("upload/hashcodecontainers").build().toUriString();
        String encodedContainerContent = encodeBase64String(hashcodeContainer.getHashcodeContainer());
        UploadHashcodeContainerRequest request = new UploadHashcodeContainerRequest();
        request.setContainer(encodedContainerContent);
        return restTemplate.postForObject(endpoint, request, UploadHashcodeContainerResponse.class);
    }

    private <T> void getSignatureList(String containerEndpoint, String containerId, Class<T> clazz) {
        String endpoint = getSigaApiUri(containerEndpoint, containerId, "signatures");
        T response = restTemplate.getForObject(endpoint, clazz);
        sendStatus(GET, endpoint, response);
    }

    private <T> T prepareMobileIdSignatureSigning(String containerEndpoint, String containerId, Class<T> clazz, Object request) {
        String endpoint = getSigaApiUri(containerEndpoint, containerId, "mobileidsigning");
        T response = restTemplate.postForObject(endpoint, request, clazz);
        sendStatus(POST, endpoint, request, response);
        return response;
    }

    @SneakyThrows
    private boolean getMobileSigningStatus(String containerEndpoint, String containerId, String generatedSignatureId) {
        String endpoint = getSigaApiUri(containerEndpoint, containerId, "mobileidsigning", generatedSignatureId, "status");
        GetContainerMobileIdSigningStatusResponse response;
        for (int i = 0; i < 6; i++) {
            response = restTemplate.getForObject(endpoint, GetContainerMobileIdSigningStatusResponse.class);
            sendStatus(GET, endpoint, response);
            if (!"SIGNATURE".equals(response.getMidStatus())) {
                Thread.sleep(5000);
            } else {
                return true;
            }
        }
        return false;
    }

    private <T> void getContainerValidation(String containerEndpoint, String containerId, Class<T> clazz) {
        String endpoint = getSigaApiUri(containerEndpoint, containerId, "validationreport");
        T response = restTemplate.getForObject(endpoint, clazz);
        sendStatus(GET, endpoint, response);
    }

    private <T> T getContainer(String containerEndpoint, String containerId, Class<T> clazz) {
        String endpoint = getSigaApiUri(containerEndpoint, containerId);
        T response = restTemplate.getForObject(endpoint, clazz);

        ProcessingStatus processingStatus = ProcessingStatus.builder()
                .containerReadyForDownload(true)
                .requestMethod(GET.name())
                .apiEndpoint(endpoint)
                .apiResponseObject(response).build();
        messagingTemplate.convertAndSend(websocketChannelId, processingStatus);

        return response;
    }

    private <T> void deleteContainer(String containerEndpoint, String containerId, Class<T> clazz) {
        String endpoint = getSigaApiUri(containerEndpoint, containerId);
        ResponseEntity<T> response = restTemplate.exchange(endpoint, DELETE, null, clazz);
        sendStatus(DELETE, endpoint, response.getStatusCode());
    }

    private void sendError(String message, String... messageArgs) {
        ProcessingStatus processingStatus = ProcessingStatus.builder().errorMessage(format(message, (Object[]) messageArgs)).build();
        messagingTemplate.convertAndSend(websocketChannelId, processingStatus);
    }

    private void sendStatus(HttpMethod requestMethod, String apiEndpoint, Object apiResponseObj) {
        sendStatus(requestMethod, apiEndpoint, null, apiResponseObj);
    }

    private void sendStatus(HttpMethod requestMethod, String apiEndpoint, Object apiRequestObj, Object apiResponseObj) {
        ProcessingStatus processingStatus = ProcessingStatus.builder()
                .requestMethod(requestMethod.name())
                .apiEndpoint(apiEndpoint)
                .apiRequestObject(apiRequestObj)
                .apiResponseObject(apiResponseObj).build();
        messagingTemplate.convertAndSend(websocketChannelId, processingStatus);
    }

    private String getSigaApiUri(String containerPath, String... pathSegments) {
        return fromUriString(sigaApiUri).path(containerPath).pathSegment(pathSegments).build().toUriString();
    }

    private CreateHashcodeContainerMobileIdSigningRequest createHashcodeMobileIdRequest(MobileSigningRequest mobileSigningRequest) {
        CreateHashcodeContainerMobileIdSigningRequest request = new CreateHashcodeContainerMobileIdSigningRequest();
        request.setMessageToDisplay("SiGa DEMO app");
        request.setSignatureProfile(SIGNATURE_PROFILE_LT);
        request.setPersonIdentifier(mobileSigningRequest.getPersonIdentifier());
        request.setLanguage("EST");
        request.setPhoneNo(mobileSigningRequest.getPhoneNr());
        return request;
    }

    private CreateContainerMobileIdSigningRequest createAsicMobileIdRequest(MobileSigningRequest mobileSigningRequest) {
        CreateContainerMobileIdSigningRequest request = new CreateContainerMobileIdSigningRequest();
        request.setMessageToDisplay("SiGa DEMO app");
        request.setSignatureProfile(SIGNATURE_PROFILE_LT);
        request.setPersonIdentifier(mobileSigningRequest.getPersonIdentifier());
        request.setLanguage("EST");
        request.setPhoneNo(mobileSigningRequest.getPhoneNr());
        return request;
    }

    private void startHashcodeSmartIdSigningFlow(SmartIdSigningRequest smartIdSigningRequest) {
        String containerId = smartIdSigningRequest.getContainerId();

        getSignatureList(HASHCODE_ENDPOINT, containerId, GetHashcodeContainerSignaturesResponse.class);
        CreateHashcodeContainerSmartIdCertificateChoiceRequest smartIdRequest = createHashcodeSmartIdRequest(smartIdSigningRequest);
        CreateHashcodeContainerSmartIdCertificateChoiceResponse smartIdResponse = prepareSmartIdCertificateSelection(containerId, smartIdRequest);

        String generatedCertificateId = smartIdResponse.getGeneratedCertificateId();
        if (StringUtils.isNotBlank(generatedCertificateId)) {
            startHashcodeSmartIdSigning(containerId, generatedCertificateId);
        }
    }

    private CreateHashcodeContainerSmartIdCertificateChoiceRequest createHashcodeSmartIdRequest(SmartIdSigningRequest smartIdSigningRequest) {
        CreateHashcodeContainerSmartIdCertificateChoiceRequest request = new CreateHashcodeContainerSmartIdCertificateChoiceRequest();
        request.setPersonIdentifier(smartIdSigningRequest.getPersonIdentifier());
        request.setCountry(smartIdSigningRequest.getCountry());
        return request;
    }

    private CreateHashcodeContainerSmartIdCertificateChoiceResponse prepareSmartIdCertificateSelection(String containerId, Object request) {
        String endpoint = getSigaApiUri(HASHCODE_ENDPOINT, containerId, "smartidsigning/certificatechoice");
        CreateHashcodeContainerSmartIdCertificateChoiceResponse response = restTemplate.postForObject(endpoint, request, CreateHashcodeContainerSmartIdCertificateChoiceResponse.class);
        sendStatus(POST, endpoint, request, response);
        return response;
    }

    private void startHashcodeSmartIdSigning(String containerId, String generatedCertificateId) {
        SmartIdCertificateChoiceStatusResponseWrapper wrapper = getSmartIdCertificateSelectionStatus(containerId, generatedCertificateId);
        if (!wrapper.isPollingSuccess()) {
            return;
        }

        String documentNumber = wrapper.getResponse().getDocumentNumber();
        CreateHashcodeContainerSmartIdSigningRequest smartIdSignatureSigningRequest = createHashcodeSmartIdSigningRequest(documentNumber);
        CreateHashcodeContainerSmartIdSigningResponse smartIdSignatureSigningResponse = prepareSmartIdSignatureSigning(containerId, smartIdSignatureSigningRequest);
        String generatedSignatureId = smartIdSignatureSigningResponse.getGeneratedSignatureId();

        if (StringUtils.isNotBlank(generatedSignatureId)) {
            if (getSmartIdSigningStatus(containerId, generatedSignatureId)) {
                endHashcodeContainerFlow(containerId);
            }
        }
    }

    @SneakyThrows
    private SmartIdCertificateChoiceStatusResponseWrapper getSmartIdCertificateSelectionStatus(String containerId, String generatedSignatureId) {
        String endpoint = getSigaApiUri(HASHCODE_ENDPOINT, containerId, "smartidsigning/certificatechoice", generatedSignatureId, "status");
        SmartIdCertificateChoiceStatusResponseWrapper wrapper = new SmartIdCertificateChoiceStatusResponseWrapper();
        GetContainerSmartIdCertificateChoiceStatusResponse response;
        for (int i = 0; i < 18; i++) {
            response = restTemplate.getForObject(endpoint, GetContainerSmartIdCertificateChoiceStatusResponse.class);
            sendStatus(GET, endpoint, response);
            if (!"CERTIFICATE".equals(response.getSidStatus())) {
                Thread.sleep(5000);
            } else {
                wrapper.setPollingSuccess(true);
                wrapper.setResponse(response);
                return wrapper;
            }
        }
        wrapper.setPollingSuccess(false);
        return wrapper;
    }

    private CreateHashcodeContainerSmartIdSigningRequest createHashcodeSmartIdSigningRequest(String documentNumber) {
        CreateHashcodeContainerSmartIdSigningRequest request = new CreateHashcodeContainerSmartIdSigningRequest();
        request.setMessageToDisplay("SiGa DEMO app");
        request.setSignatureProfile(SIGNATURE_PROFILE_LT);
        request.setDocumentNumber(documentNumber);
        return request;
    }

    private CreateHashcodeContainerSmartIdSigningResponse prepareSmartIdSignatureSigning(String containerId, Object request) {
        String endpoint = getSigaApiUri(HASHCODE_ENDPOINT, containerId, "smartidsigning");
        CreateHashcodeContainerSmartIdSigningResponse response = restTemplate.postForObject(endpoint, request, CreateHashcodeContainerSmartIdSigningResponse.class);
        sendStatus(POST, endpoint, request, response);
        return response;
    }

    @SneakyThrows
    private boolean getSmartIdSigningStatus(String containerId, String generatedSignatureId) {
        String endpoint = getSigaApiUri(HASHCODE_ENDPOINT, containerId, "smartidsigning", generatedSignatureId, "status");
        GetContainerSmartIdSigningStatusResponse response;
        for (int i = 0; i < 6; i++) {
            response = restTemplate.getForObject(endpoint, GetContainerSmartIdSigningStatusResponse.class);
            sendStatus(GET, endpoint, response);
            if (!"SIGNATURE".equals(response.getSidStatus())) {
                Thread.sleep(5000);
            } else {
                return true;
            }
        }
        return false;
    }

    class RestTemplateResponseErrorHandler implements ResponseErrorHandler {

        @Override
        public boolean hasError(ClientHttpResponse httpResponse) throws IOException {
            log.info("HttpResponse: {}, {}", httpResponse.getStatusCode(), httpResponse.getStatusText());
            return (httpResponse.getStatusCode().series() != SUCCESSFUL);
        }

        @Override
        public void handleError(ClientHttpResponse httpResponse) throws IOException {
            sendError(format("Unable to process container: {0}, {1}", httpResponse.getStatusCode(), httpResponse.getStatusText()));
        }
    }
}
