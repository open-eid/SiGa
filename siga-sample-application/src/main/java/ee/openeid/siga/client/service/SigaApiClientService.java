package ee.openeid.siga.client.service;

import ee.openeid.siga.client.hmac.HmacTokenAuthorizationHeaderInterceptor;
import ee.openeid.siga.client.model.Container;
import ee.openeid.siga.client.model.MobileSigningRequest;
import ee.openeid.siga.client.model.ProcessingStatus;
import ee.openeid.siga.client.model.ProcessingStatus.Status;
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

import static ee.openeid.siga.client.model.ProcessingStatus.Status.*;
import static java.text.MessageFormat.format;
import static org.apache.tomcat.util.codec.binary.Base64.encodeBase64String;
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
    private ContainerCacheService containerCacheService;
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
            containerId = containerCacheService.get(fileId).getId();
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
        return containerCacheService.cache(containerId, containerId + ".asice", Base64.getDecoder().decode(getContainerResponse.getContainer()));
    }

    private String uploadContainer(String fileId) {
        String encodedContainerContent = encodeBase64String(containerCacheService.get(fileId).getFile());
        UploadHashcodeContainerRequest uploadHashcodeContainerRequest = new UploadHashcodeContainerRequest();
        uploadHashcodeContainerRequest.setContainer(encodedContainerContent);
        UploadHashcodeContainerResponse response = restTemplate.postForObject(fromUriString(sigaApiUri).path("upload/hashcodecontainers").build().toUriString(), uploadHashcodeContainerRequest, UploadHashcodeContainerResponse.class);
        log.info("Returned container id {} for uploaded file id {}", response.getContainerId(), fileId);
        sendStatusToWebsocket(START, "Returned container id {0} for uploaded file id {1}", response.getContainerId(), fileId);
        return response.getContainerId();
    }

    private void getSignatureList(String containerId) {
        GetHashcodeContainerSignaturesResponse response = restTemplate.getForObject(getSigaApiUri(containerId, "signatures"), GetHashcodeContainerSignaturesResponse.class);
        sendStatusToWebsocket(PROCESSING, response, "Signatures");
    }

    private boolean prepareMobileIdSignatureSigning(MobileSigningRequest mobileSigningRequest, String containerId) {
        CreateHashcodeContainerMobileIdSigningRequest createMobileIdSigningRequest = new CreateHashcodeContainerMobileIdSigningRequest();
        createMobileIdSigningRequest.setMessageToDisplay("SiGa DEMO app");
        createMobileIdSigningRequest.setSignatureProfile("LT");
        createMobileIdSigningRequest.setPersonIdentifier(mobileSigningRequest.getPersonIdentifier());
        createMobileIdSigningRequest.setCountry(mobileSigningRequest.getCountry());
        createMobileIdSigningRequest.setLanguage("EST");
        createMobileIdSigningRequest.setPhoneNo(mobileSigningRequest.getPhoneNr());
        CreateHashcodeContainerMobileIdSigningResponse mobileidsigningResponse = restTemplate.postForObject(getSigaApiUri(containerId, "mobileidsigning"), createMobileIdSigningRequest, CreateHashcodeContainerMobileIdSigningResponse.class);
        sendStatusToWebsocket(CHALLENGE, mobileidsigningResponse, "Mobile signing challenge: {0}", mobileidsigningResponse.getChallengeId());
        return StringUtils.isNotBlank(mobileidsigningResponse.getChallengeId());
    }

    @SneakyThrows
    private boolean getMobileSigningStatus(String containerId) {
        GetHashcodeContainerMobileIdSigningStatusResponse response;
        for (int i = 0; i < 6; i++) {
            response = restTemplate.getForObject(getSigaApiUri(containerId, "mobileidsigning", "status"), GetHashcodeContainerMobileIdSigningStatusResponse.class);
            sendStatusToWebsocket(PROCESSING, "Mobile signing status: {0}", response.getMidStatus());
            if (!"SIGNATURE".equals(response.getMidStatus())) {
                Thread.sleep(5000);
            } else {
                return true;
            }
        }
        sendStatusToWebsocket(ERROR, "Unable to sign container");
        return false;
    }

    private void getContainerValidation(String containerId) {
        GetHashcodeContainerValidationReportResponse response = restTemplate.getForObject(getSigaApiUri(containerId, "validationreport"), GetHashcodeContainerValidationReportResponse.class);
        sendStatusToWebsocket(VALIDATION, response, "Validation conclusion");
    }

    private Container getContainer(String fileId, String containerId) {
        GetHashcodeContainerResponse response = restTemplate.getForObject(getSigaApiUri(containerId), GetHashcodeContainerResponse.class);
        Container container = containerCacheService.get(fileId);
        log.info("Caching container file {} with id {}", container.getFileName(), container.getId());
        sendStatusToWebsocket(RESULT, "Container {0} ready to download", containerId);
        return containerCacheService.cache(fileId, container.getFileName(), Base64.getDecoder().decode(response.getContainer()));
    }

    private void deleteContainer(String containerId) {
        ResponseEntity<DeleteHashcodeContainerResponse> response = restTemplate.exchange(getSigaApiUri(containerId), HttpMethod.DELETE, null, DeleteHashcodeContainerResponse.class);
        sendStatusToWebsocket(FINISH, "Delete container result from SiGa service: {0}", response.getBody().getResult());
    }

    private void sendStatusToWebsocket(Status status, String message, String... messageArgs) {
        sendStatusToWebsocket(status, null, message, messageArgs);
    }

    private void sendStatusToWebsocket(Status status, Object apiResponseObj, String message, String... messageArgs) {
        final String formattedMessage = format(message, messageArgs);
        log.info(formattedMessage);
        ProcessingStatus processingStatus = ProcessingStatus.builder().status(status).message(formattedMessage).apiResponseObject(apiResponseObj).build();
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
            sendStatusToWebsocket(ERROR, format("Unable to process container: {0}, {1}", httpResponse.getStatusCode(), httpResponse.getStatusText()));
        }
    }
}
