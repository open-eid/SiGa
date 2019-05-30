package ee.openeid.siga;

import ee.openeid.siga.common.DataToSignWrapper;
import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.service.signature.container.attached.AttachedDataFileContainerService;
import ee.openeid.siga.service.signature.container.attached.AttachedDataFileContainerSigningService;
import ee.openeid.siga.service.signature.container.attached.AttachedDataFileContainerValidationService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.*;
import org.digidoc4j.DataToSign;
import org.digidoc4j.SignatureParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;

@RestController
public class AttachedDataFileContainerController {

    private AttachedDataFileContainerService containerService;
    private AttachedDataFileContainerValidationService validationService;
    private AttachedDataFileContainerSigningService signingService;

    @SigaEventLog(eventName = SigaEventName.CREATE_CONTAINER, logParameters = {@Param(index = 0, fields = {@XPath(name = "no_of_datafiles", xpath = "helper:size(dataFiles)")})}, logReturnObject = {@XPath(name = "container_id", xpath = "containerId")})
    @RequestMapping(value = "/containers", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateContainerResponse createContainer(@RequestBody CreateContainerRequest createContainerRequest) {
        List<DataFile> dataFiles = createContainerRequest.getDataFiles();
        String containerName = createContainerRequest.getContainerName();
        RequestValidator.validateDataFiles(dataFiles);
        RequestValidator.validateContainerName(containerName);

        String sessionId = containerService.createContainer(containerName, RequestTransformer.transformDataFiles(dataFiles));
        CreateContainerResponse response = new CreateContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.UPLOAD_CONTAINER, logReturnObject = {@XPath(name = "container_id", xpath = "containerId")})
    @RequestMapping(value = "/upload/containers", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public UploadContainerResponse uploadContainer(@RequestBody UploadContainerRequest uploadContainerRequest) {
        String container = uploadContainerRequest.getContainer();
        String containerName = uploadContainerRequest.getContainerName();
        RequestValidator.validateFileContent(container);
        RequestValidator.validateContainerName(containerName);
        String sessionId = containerService.uploadContainer(containerName, container);
        UploadContainerResponse response = new UploadContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.VALIDATE_CONTAINER)
    @RequestMapping(value = "/containers/validationreport", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateContainerValidationReportResponse validateContainer(@RequestBody CreateContainerValidationReportRequest validationReportRequest) {
        String container = validationReportRequest.getContainer();
        String containerName = validationReportRequest.getContainerName();
        RequestValidator.validateContainerName(containerName);
        RequestValidator.validateFileContent(container);

        ValidationConclusion validationConclusion = validationService.validateContainer(containerName, container);
        CreateContainerValidationReportResponse response = new CreateContainerValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.VALIDATE_CONTAINER_BY_ID)
    @RequestMapping(value = "/containers/{containerId}/validationreport", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetContainerValidationReportResponse getContainerValidation(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        ValidationConclusion validationConclusion = validationService.validateExistingContainer(containerId);
        GetContainerValidationReportResponse response = new GetContainerValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }


    @SigaEventLog(eventName = SigaEventName.REMOTE_SIGNING_INIT, logParameters = {@Param(index = 1, fields = {@XPath(name = "signature_profile", xpath = "signatureProfile")})})
    @RequestMapping(value = "/containers/{containerId}/remotesigning", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateContainerRemoteSigningResponse prepareRemoteSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateContainerRemoteSigningRequest createRemoteSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateRemoteSigning(createRemoteSigningRequest.getSigningCertificate(), createRemoteSigningRequest.getSignatureProfile());

        String signingCertificate = createRemoteSigningRequest.getSigningCertificate();
        String signatureProfile = createRemoteSigningRequest.getSignatureProfile();
        SignatureProductionPlace signatureProductionPlace = createRemoteSigningRequest.getSignatureProductionPlace();
        List<String> roles = createRemoteSigningRequest.getRoles();

        SignatureParameters signatureParameters = RequestTransformer.transformRemoteRequest(signingCertificate, signatureProfile, signatureProductionPlace, roles);
        DataToSignWrapper dataToSignWrapper = signingService.createDataToSign(containerId, signatureParameters);
        DataToSign dataToSign = dataToSignWrapper.getDataToSign();
        CreateContainerRemoteSigningResponse response = new CreateContainerRemoteSigningResponse();
        response.setGeneratedSignatureId(dataToSign.getSignatureParameters().getSignatureId());
        response.setDataToSign(new String(Base64.getEncoder().encode(dataToSign.getDataToSign())));
        response.setDigestAlgorithm(dataToSign.getDigestAlgorithm().name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.REMOTE_SIGNING_FINISH)
    @RequestMapping(value = "/containers/{containerId}/remotesigning/{signatureId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.PUT)
    public UpdateContainerRemoteSigningResponse finalizeRemoteSignature(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId, @RequestBody UpdateContainerRemoteSigningRequest updateRemoteSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureValue(updateRemoteSigningRequest.getSignatureValue());
        String result = signingService.finalizeSigning(containerId, signatureId, updateRemoteSigningRequest.getSignatureValue());
        UpdateContainerRemoteSigningResponse response = new UpdateContainerRemoteSigningResponse();
        response.setResult(result);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.GET_SIGNATURES_LIST)
    @RequestMapping(value = "/containers/{containerId}/signatures", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetContainerSignaturesResponse getSignatureList(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        List<ee.openeid.siga.common.Signature> signatures = containerService.getSignatures(containerId);
        GetContainerSignaturesResponse response = new GetContainerSignaturesResponse();
        response.getSignatures().addAll(RequestTransformer.transformSignatures(signatures));
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.GET_CONTAINER)
    @RequestMapping(value = "/containers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetContainerResponse getContainer(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        String container = containerService.getContainer(containerId);
        GetContainerResponse response = new GetContainerResponse();
        response.setContainer(container);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.DELETE_CONTAINER)
    @RequestMapping(value = "/containers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.DELETE)
    public DeleteContainerResponse closeSession(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);
        String result = containerService.closeSession(containerId);
        DeleteContainerResponse response = new DeleteContainerResponse();
        response.setResult(result);
        return response;
    }

    @Autowired
    public void setContainerService(AttachedDataFileContainerService containerService) {
        this.containerService = containerService;
    }

    @Autowired
    public void setValidationService(AttachedDataFileContainerValidationService validationService) {
        this.validationService = validationService;
    }
}
