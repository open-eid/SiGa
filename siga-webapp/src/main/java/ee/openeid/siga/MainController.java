package ee.openeid.siga;

import ee.openeid.siga.service.signature.DetachedDataFileContainerService;
import ee.openeid.siga.service.signature.DetachedDataFileContainerValidationService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class MainController {

    private DetachedDataFileContainerService containerService;
    private DetachedDataFileContainerValidationService validationService;

    @RequestMapping(value = "/hashcodecontainers", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashCodeContainerResponse createContainer(@RequestBody CreateHashCodeContainerRequest createContainerRequest) {
        List<HashCodeDataFile> dataFiles = createContainerRequest.getDataFiles();
        RequestValidator.validateHashCodeDataFiles(dataFiles);

        CreateHashCodeContainerResponse response = new CreateHashCodeContainerResponse();
        String sessionId = containerService.createContainer(dataFiles);
        response.setContainerId(sessionId);
        return response;
    }

    @RequestMapping(value = "/upload/hashcodecontainers", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public UploadHashCodeContainerResponse uploadContainer(@RequestBody UploadHashCodeContainerRequest uploadContainerRequest) {
        String container = uploadContainerRequest.getContainer();
        RequestValidator.validateFileContent(container);

        UploadHashCodeContainerResponse response = new UploadHashCodeContainerResponse();
        String sessionId = containerService.uploadContainer(container);
        response.setContainerId(sessionId);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/validationreport", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashCodeValidationReportResponse validateContainer(@RequestBody CreateHashCodeValidationReportRequest validationReportRequest) {
        String container = validationReportRequest.getContainer();
        RequestValidator.validateFileContent(container);

        CreateHashCodeValidationReportResponse response = new CreateHashCodeValidationReportResponse();
        ValidationConclusion validationConclusion = validationService.validateContainer(container);
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/validationreport", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashCodeValidationReportResponse getContainerValidation(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        GetHashCodeValidationReportResponse response = new GetHashCodeValidationReportResponse();
        ValidationConclusion validationConclusion = validationService.validateExistingContainer(containerId);
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/remotesigning", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashCodeRemoteSigningResponse prepareRemoteSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashCodeRemoteSigningRequest createRemoteSigningRequest) {
        return null;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/remotesigning", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.PUT)
    public UpdateHashCodeRemoteSigningResponse finalizeRemoteSignature(@PathVariable(value = "containerId") String containerId, @RequestBody UpdateHashCodeRemoteSigningRequest updateRemoteSigningRequest) {
        return null;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/mobileidsigning", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashCodeMobileIdSigningResponse prepareMobileIdSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashCodeMobileIdSigningRequest createMobileIdSigningRequest) {
        return null;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/mobileidsigning/status", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashCodeMobileIdSigningStatusResponse getMobileSigningStatus(@PathVariable(value = "containerId") String containerId) {
        return null;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/signatures", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashCodeSignaturesResponse getSignatureList(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);
        GetHashCodeSignaturesResponse response = new GetHashCodeSignaturesResponse();
        List<Signature> signatures = containerService.getSignatures(containerId);
        response.getSignatures().addAll(signatures);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashCodeContainerResponse getContainer(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);
        GetHashCodeContainerResponse response = new GetHashCodeContainerResponse();
        String container = containerService.getContainer(containerId);
        response.setContainer(container);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.DELETE)
    public DeleteHashCodeContainerResponse closeSession(@PathVariable(value = "containerId") String containerId) {
        return null;
    }

    @Autowired
    protected void setContainerService(DetachedDataFileContainerService containerService) {
        this.containerService = containerService;
    }

    @Autowired
    protected void setValidationService(DetachedDataFileContainerValidationService validationService) {
        this.validationService = validationService;
    }
}
