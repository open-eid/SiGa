package ee.openeid.siga.client.service;

import ee.openeid.siga.client.hmac.HmacTokenAuthorizationHeaderInterceptor;
import ee.openeid.siga.client.model.AsicContainerWrapper;
import ee.openeid.siga.client.model.GetContainerMobileIdSigningStatusResponse;
import ee.openeid.siga.client.model.HashcodeContainerWrapper;
import ee.openeid.siga.client.model.MobileSigningRequest;
import ee.openeid.siga.client.model.ProcessingStatus;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
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
import static org.springframework.http.HttpMethod.*;
import static org.springframework.http.HttpStatus.Series.SUCCESSFUL;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;

@Slf4j
@Service
@RequestScope
public class SigaApiClientService {
    private final String hmacAlgorithm;
    private final String hmacServiceUuid;
    private final String hmacSharedSigningKey;
    private final String sigaApiUri;
    private RestTemplate restTemplate;
    private String websocketChannelId;
    private final static String ASIC_ENDPOINT = "containers";
    private final static String HASHCODE_ENDPOINT = "hashcodecontainers";
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
                .loadTrustMaterial(ResourceUtils.getFile(trustStore), trustStorePassword.toCharArray())
                .build();
        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext);
        HttpClient httpClient = HttpClients.custom().setSSLSocketFactory(socketFactory).setSSLHostnameVerifier(new NoopHostnameVerifier()).build();

        restTemplate = restTemplateBuilder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .interceptors(new HmacTokenAuthorizationHeaderInterceptor(sigaApiUri, hmacAlgorithm, hmacServiceUuid, hmacSharedSigningKey))
                .errorHandler(new RestTemplateResponseErrorHandler()).build();
    }

    @Async
    @SneakyThrows
    public void startMobileSigningFlow(MobileSigningRequest mobileSigningRequest) {
        String fileId = mobileSigningRequest.getFileId();
        setUpClientNotificationChannel(fileId);

        String containerId;
        if (mobileSigningRequest.isContainerCreated()) {
            if (MobileSigningRequest.ContainerType.HASHCODE == mobileSigningRequest.getContainerType()) {
                log.info("Container is already uploaded. Getting container {} from cacheHashcodeContainer", fileId);
                containerId = containerService.getHashcodeContainer(fileId).getId();
            } else {
                log.info("Container is already uploaded. Getting container {} from cacheAsicContainer", fileId);
                containerId = containerService.getAsicContainer(fileId).getId();
            }

        } else {
            log.info("Uploading container file with id: {}", fileId);
            containerId = uploadContainer(fileId);
        }

        if (MobileSigningRequest.ContainerType.HASHCODE == mobileSigningRequest.getContainerType()) {
            startHashcodeMobileIdSigningFlow(containerId, mobileSigningRequest);

        } else {
            startAsicMobileIdSigningFlow(containerId, mobileSigningRequest);
        }

    }

    private void startAsicMobileIdSigningFlow(String containerId, MobileSigningRequest mobileSigningRequest) {
        getSignatureList(ASIC_ENDPOINT, containerId, GetContainerSignaturesResponse.class);
        CreateContainerMobileIdSigningRequest mobileIdRequest = createAsicMobileIdRequest(mobileSigningRequest);
        CreateContainerMobileIdSigningResponse mobileIdResponse =
                prepareMobileIdSignatureSigning(ASIC_ENDPOINT, containerId, CreateContainerMobileIdSigningResponse.class, mobileIdRequest);
        String generatedSignatureId = mobileIdResponse.getGeneratedSignatureId();
        if (StringUtils.isNotBlank(generatedSignatureId)) {

            if (getMobileSigningStatus(ASIC_ENDPOINT, containerId, generatedSignatureId)) {
                getContainerValidation(ASIC_ENDPOINT, containerId, GetContainerValidationReportResponse.class);
                GetContainerResponse getContainerResponse = getContainer(ASIC_ENDPOINT, containerId, GetContainerResponse.class);
                AsicContainerWrapper container = containerService.getAsicContainer(mobileSigningRequest.getFileId());
                containerService.cacheAsicContainer(mobileSigningRequest.getFileId(), container.getName(), Base64.getDecoder().decode(getContainerResponse.getContainer()));
                deleteContainer(ASIC_ENDPOINT, containerId, DeleteContainerResponse.class);
            }
        }
    }

    private void startHashcodeMobileIdSigningFlow(String containerId, MobileSigningRequest mobileSigningRequest) {
        getSignatureList(HASHCODE_ENDPOINT, containerId, GetHashcodeContainerSignaturesResponse.class);
        CreateHashcodeContainerMobileIdSigningRequest mobileIdRequest = createHashcodeMobileIdRequest(mobileSigningRequest);
        CreateHashcodeContainerMobileIdSigningResponse mobileIdResponse =
                prepareMobileIdSignatureSigning(HASHCODE_ENDPOINT, containerId, CreateHashcodeContainerMobileIdSigningResponse.class, mobileIdRequest);

        String generatedSignatureId = mobileIdResponse.getGeneratedSignatureId();

        if (StringUtils.isNotBlank(generatedSignatureId)) {

            if (getMobileSigningStatus(HASHCODE_ENDPOINT, containerId, generatedSignatureId)) {
                getContainerValidation(HASHCODE_ENDPOINT, containerId, GetHashcodeContainerValidationReportResponse.class);
                GetHashcodeContainerResponse getContainerResponse = getContainer(HASHCODE_ENDPOINT, containerId, GetHashcodeContainerResponse.class);
                HashcodeContainerWrapper container = containerService.getHashcodeContainer(mobileSigningRequest.getFileId());
                containerService.cacheHashcodeContainer(mobileSigningRequest.getFileId(), container.getFileName(), Base64.getDecoder().decode(getContainerResponse.getContainer()), container.getOriginalDataFiles());
                deleteContainer(HASHCODE_ENDPOINT, containerId, DeleteHashcodeContainerResponse.class);
            }
        }
    }

    private void setUpClientNotificationChannel(String fileId) {
        websocketChannelId = "/progress/" + fileId;
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

    private String uploadContainer(String fileId) {
        String endpoint = fromUriString(sigaApiUri).path("upload/hashcodecontainers").build().toUriString();
        String encodedContainerContent = encodeBase64String(containerService.getHashcodeContainer(fileId).getHashcodeContainer());
        UploadHashcodeContainerRequest request = new UploadHashcodeContainerRequest();
        request.setContainer(encodedContainerContent);
        UploadHashcodeContainerResponse response = restTemplate.postForObject(endpoint, request, UploadHashcodeContainerResponse.class);
        log.info("Container id {} for uploaded file id {}", response.getContainerId(), fileId);
        sendStatus(POST, endpoint, request, response);
        return response.getContainerId();
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
        request.setSignatureProfile("LT");
        request.setPersonIdentifier(mobileSigningRequest.getPersonIdentifier());
        request.setLanguage("EST");
        request.setPhoneNo(mobileSigningRequest.getPhoneNr());
        return request;
    }

    private CreateContainerMobileIdSigningRequest createAsicMobileIdRequest(MobileSigningRequest mobileSigningRequest) {
        CreateContainerMobileIdSigningRequest request = new CreateContainerMobileIdSigningRequest();
        request.setMessageToDisplay("SiGa DEMO app");
        request.setSignatureProfile("LT");
        request.setPersonIdentifier(mobileSigningRequest.getPersonIdentifier());
        request.setLanguage("EST");
        request.setPhoneNo(mobileSigningRequest.getPhoneNr());
        return request;
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
