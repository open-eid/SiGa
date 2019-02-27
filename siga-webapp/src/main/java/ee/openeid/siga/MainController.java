package ee.openeid.siga;

import ee.openeid.siga.service.signature.HashCodeContainerService;
import ee.openeid.siga.service.signature.ValidationService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
public class MainController {

    private HashCodeContainerService containerService;
    private ValidationService validationService;

    @RequestMapping(value = "/hashcodecontainers", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashCodeContainerResponse createContainer(@RequestBody CreateHashCodeContainerRequest createContainerRequest) {
        RequestValidator.validateCreateContainerRequest(createContainerRequest);
        return containerService.createContainer(createContainerRequest);
    }

    @RequestMapping(value = "/upload/hashcodecontainers", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public UploadHashCodeContainerResponse uploadContainer(@RequestBody UploadHashCodeContainerRequest uploadContainerRequest) {
        RequestValidator.validateUploadContainerRequest(uploadContainerRequest);
        return containerService.uploadContainer(uploadContainerRequest);
    }

    @RequestMapping(value = "/hashcodecontainers/validationreport", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashCodeValidationReportResponse validateContainer(@RequestBody CreateHashCodeValidationReportRequest validationReportRequest) {
        RequestValidator.validateValidationReportRequest(validationReportRequest);
        return validationService.validateContainer(validationReportRequest);
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/validationreport", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashCodeValidationReportResponse getContainerValidation(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);
        return validationService.validateExistingContainer(containerId);
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
        return null;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashCodeContainerResponse getContainer(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);
        return containerService.getContainer(containerId);
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.DELETE)
    public DeleteHashCodeContainerResponse closeSession(@PathVariable(value = "containerId") String containerId) {
        return null;
    }

    @Autowired
    protected void setContainerService(HashCodeContainerService containerService) {
        this.containerService = containerService;
    }

    @Autowired
    protected void setValidationService(ValidationService validationService) {
        this.validationService = validationService;
    }
}
