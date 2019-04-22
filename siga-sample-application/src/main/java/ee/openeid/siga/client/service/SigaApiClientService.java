package ee.openeid.siga.client.service;

import ee.openeid.siga.client.hmac.HmacTokenAuthorizationHeaderInterceptor;
import ee.openeid.siga.client.model.Container;
import ee.openeid.siga.client.model.MobileSigningRequest;
import ee.openeid.siga.client.model.ProcessingStatus;
import ee.openeid.siga.webapp.json.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.annotation.RequestScope;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static java.text.MessageFormat.format;
import static java.util.Objects.requireNonNull;
import static org.apache.tomcat.util.codec.binary.Base64.encodeBase64String;
import static org.springframework.http.HttpMethod.*;
import static org.springframework.http.HttpStatus.Series.CLIENT_ERROR;
import static org.springframework.http.HttpStatus.Series.SERVER_ERROR;
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

    @Autowired
    private ContainerService containerService;
    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    public SigaApiClientService(@Autowired RestTemplateBuilder restTemplateBuilder,
                                @Value("${siga.client.hmac.algorithm}") String hmacAlgorithm,
                                @Value("${siga.client.hmac.service-uuid}") String hmacServiceUuid,
                                @Value("${siga.client.hmac.shared-signing-key}") String hmacSharedSigningKey,
                                @Value("${siga.api.uri}") String sigaApiUri) {
        this.hmacAlgorithm = hmacAlgorithm;
        this.hmacServiceUuid = hmacServiceUuid;
        this.hmacSharedSigningKey = hmacSharedSigningKey;
        this.sigaApiUri = sigaApiUri;
        setUpRestTemplateForRequestScope(restTemplateBuilder);
    }

    private void setUpRestTemplateForRequestScope(RestTemplateBuilder restTemplateBuilder) {
        restTemplate = restTemplateBuilder
                .interceptors(new HmacTokenAuthorizationHeaderInterceptor(hmacAlgorithm, hmacServiceUuid, hmacSharedSigningKey))
                .errorHandler(new RestTemplateResponseErrorHandler()).build();
    }

    @Async
    @SneakyThrows
    public void startMobileSigningFlow(MobileSigningRequest mobileSigningRequest) {
        String fileId = mobileSigningRequest.getFileId();
        setUpClientNotificationChannel(fileId);

        String containerId = null;
        if (mobileSigningRequest.isContainerCreated()) {
            log.info("Container is already uploaded. Getting container {} from cache", fileId);
            containerId = containerService.get(fileId).getId();
        } else {
            log.info("Uploading container file with id: {}", fileId);
            containerId = uploadContainer(fileId);
        }

        getSignatureList(containerId);
        if (prepareMobileIdSignatureSigning(mobileSigningRequest, containerId)) {
            if (getMobileSigningStatus(containerId)) {
                getContainerValidation(containerId);
                getContainer(fileId, containerId);
                deleteContainer(containerId);
            }
        }
    }

    private void setUpClientNotificationChannel(String fileId) {
        websocketChannelId = "/progress/" + fileId;
    }

    public Container createContainer(List<HashcodeDataFile> files) {
        CreateHashcodeContainerRequest request = new CreateHashcodeContainerRequest();
        files.forEach(f -> request.getDataFiles().add(f));
        CreateHashcodeContainerResponse createContainerResponse = restTemplate.postForObject(fromUriString(sigaApiUri).path("hashcodecontainers").build().toUriString(), request, CreateHashcodeContainerResponse.class);
        String containerId = createContainerResponse.getContainerId();
        GetHashcodeContainerResponse getContainerResponse = restTemplate.getForObject(getSigaApiUri(containerId), GetHashcodeContainerResponse.class);
        log.info("Created container with id {}", containerId);
        return containerService.cache(containerId, containerId + ".asice", Base64.getDecoder().decode(getContainerResponse.getContainer()));
    }

    private String uploadContainer(String fileId) {
        String endpoint = fromUriString(sigaApiUri).path("upload/hashcodecontainers").build().toUriString();
        String encodedContainerContent = encodeBase64String(containerService.get(fileId).getFile());
        UploadHashcodeContainerRequest request = new UploadHashcodeContainerRequest();
        request.setContainer(encodedContainerContent);
        UploadHashcodeContainerResponse response = restTemplate.postForObject(endpoint, request, UploadHashcodeContainerResponse.class);
        log.info("Container id {} for uploaded file id {}", response.getContainerId(), fileId);
        sendStatus(POST, endpoint, request, response);
        return response.getContainerId();
    }

    private void getSignatureList(String containerId) {
        String endpoint = getSigaApiUri(containerId, "signatures");
        GetHashcodeContainerSignaturesResponse response = restTemplate.getForObject(endpoint, GetHashcodeContainerSignaturesResponse.class);
        sendStatus(GET, endpoint, response);
    }

    private boolean prepareMobileIdSignatureSigning(MobileSigningRequest mobileSigningRequest, String containerId) {
        String endpoint = getSigaApiUri(containerId, "mobileidsigning");
        CreateHashcodeContainerMobileIdSigningRequest request = new CreateHashcodeContainerMobileIdSigningRequest();
        request.setMessageToDisplay("SiGa DEMO app");
        request.setSignatureProfile("LT");
        request.setPersonIdentifier(mobileSigningRequest.getPersonIdentifier());
        request.setCountry(mobileSigningRequest.getCountry());
        request.setLanguage("EST");
        request.setPhoneNo(mobileSigningRequest.getPhoneNr());
        CreateHashcodeContainerMobileIdSigningResponse response = restTemplate.postForObject(endpoint, request, CreateHashcodeContainerMobileIdSigningResponse.class);
        sendStatus(POST, endpoint, request, response);
        return StringUtils.isNotBlank(response.getChallengeId());
    }

    @SneakyThrows
    private boolean getMobileSigningStatus(String containerId) {
        String endpoint = getSigaApiUri(containerId, "mobileidsigning", "status");
        GetHashcodeContainerMobileIdSigningStatusResponse response;
        for (int i = 0; i < 6; i++) {
            response = restTemplate.getForObject(endpoint, GetHashcodeContainerMobileIdSigningStatusResponse.class);
            sendStatus(GET, endpoint, response);
            if (!"SIGNATURE".equals(response.getMidStatus())) {
                Thread.sleep(5000);
            } else {
                return true;
            }
        }
        return false;
    }

    private void getContainerValidation(String containerId) {
        String endpoint = getSigaApiUri(containerId, "validationreport");
        GetHashcodeContainerValidationReportResponse response = restTemplate.getForObject(endpoint, GetHashcodeContainerValidationReportResponse.class);
        sendStatus(GET, endpoint, response);
    }

    private Container getContainer(String fileId, String containerId) {
        String endpoint = getSigaApiUri(containerId);
        GetHashcodeContainerResponse response = restTemplate.getForObject(endpoint, GetHashcodeContainerResponse.class);
        Container container = containerService.get(fileId);
        requireNonNull(container.getFileName());
        log.info("Caching container file {} with id {}", container.getFileName(), container.getId());
        ProcessingStatus processingStatus = ProcessingStatus.builder()
                .containerReadyForDownload(true)
                .requestMethod(GET.name())
                .apiEndpoint(endpoint)
                .apiResponseObject(response).build();
        messagingTemplate.convertAndSend(websocketChannelId, processingStatus);
        return containerService.cache(fileId, container.getFileName(), Base64.getDecoder().decode(response.getContainer()));
    }

    private void deleteContainer(String containerId) {
        String endpoint = getSigaApiUri(containerId);
        ResponseEntity<DeleteHashcodeContainerResponse> response = restTemplate.exchange(endpoint, DELETE, null, DeleteHashcodeContainerResponse.class);
        sendStatus(DELETE, endpoint, response.getStatusCode());
    }

    private void sendError(String message, String... messageArgs) {
        ProcessingStatus processingStatus = ProcessingStatus.builder().errorMessage(format(message, messageArgs)).build();
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

    private String getSigaApiUri(String... pathSegments) {
        return fromUriString(sigaApiUri).path("hashcodecontainers").pathSegment(pathSegments).build().toUriString();
    }

    class RestTemplateResponseErrorHandler implements ResponseErrorHandler {

        @Override
        public boolean hasError(ClientHttpResponse httpResponse) throws IOException {
            log.info("HttpResponse: {}, {}", httpResponse.getStatusCode(), httpResponse.getStatusText());
            return (httpResponse.getStatusCode().series() == CLIENT_ERROR
                    || httpResponse.getStatusCode().series() == SERVER_ERROR);
        }

        @Override
        public void handleError(ClientHttpResponse httpResponse) throws IOException {
            sendError(format("Unable to process container: {0}, {1}", httpResponse.getStatusCode(), httpResponse.getStatusText()));
        }
    }
}
