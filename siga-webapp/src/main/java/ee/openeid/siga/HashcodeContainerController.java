package ee.openeid.siga;

import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.model.DataToSignWrapper;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerValidationService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.*;
import org.digidoc4j.DataToSign;
import org.digidoc4j.SignatureParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;

@RestController
public class HashcodeContainerController {

    private HashcodeContainerService containerService;
    private HashcodeContainerValidationService validationService;
    private HashcodeContainerSigningService signingService;
    private ConnectionRepository connectionRepository;

    @SigaEventLog(eventName = SigaEventName.HC_CREATE_CONTAINER, logParameters = {@Param(index = 0, fields = {@XPath(name = "no_of_datafiles", xpath = "helper:size(dataFiles)")})}, logReturnObject = {@XPath(name = "container_id", xpath = "containerId")})
    @PostMapping(value = "/hashcodecontainers", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateHashcodeContainerResponse createContainer(@RequestBody CreateHashcodeContainerRequest createContainerRequest) {
        List<HashcodeDataFile> dataFiles = createContainerRequest.getDataFiles();
        RequestValidator.validateHashcodeDataFiles(dataFiles);

        String sessionId = containerService.createContainer(RequestTransformer.transformHashcodeDataFilesForApplication(dataFiles));
        CreateHashcodeContainerResponse response = new CreateHashcodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_UPLOAD_CONTAINER, logReturnObject = {@XPath(name = "container_id", xpath = "containerId")})
    @PostMapping(value = "/upload/hashcodecontainers", produces = MediaType.APPLICATION_JSON_VALUE)
    public UploadHashcodeContainerResponse uploadContainer(@RequestBody UploadHashcodeContainerRequest uploadContainerRequest) {
        String container = uploadContainerRequest.getContainer();
        RequestValidator.validateFileContent(container);

        String sessionId = containerService.uploadContainer(container);
        UploadHashcodeContainerResponse response = new UploadHashcodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_VALIDATE_CONTAINER)
    @PostMapping(value = "/hashcodecontainers/validationreport", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateHashcodeContainerValidationReportResponse validateContainer(@RequestBody CreateHashcodeContainerValidationReportRequest validationReportRequest) {
        String container = validationReportRequest.getContainer();
        RequestValidator.validateFileContent(container);

        SigaUserDetails sigaUserDetails = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ValidationConclusion validationConclusion = validationService.validateContainer(container, sigaUserDetails.getServiceType());
        CreateHashcodeContainerValidationReportResponse response = new CreateHashcodeContainerValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_VALIDATE_CONTAINER_BY_ID)
    @GetMapping(value = "/hashcodecontainers/{containerId}/validationreport", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetHashcodeContainerValidationReportResponse getContainerValidation(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        ValidationConclusion validationConclusion = validationService.validateExistingContainer(containerId);
        GetHashcodeContainerValidationReportResponse response = new GetHashcodeContainerValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_REMOTE_SIGNING_INIT, logParameters = {@Param(index = 1, fields = {@XPath(name = "signature_profile", xpath = "signatureProfile")})})
    @PostMapping(value = "/hashcodecontainers/{containerId}/remotesigning", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateHashcodeContainerRemoteSigningResponse prepareRemoteSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashcodeContainerRemoteSigningRequest createRemoteSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateRemoteSigning(createRemoteSigningRequest.getSigningCertificate(), createRemoteSigningRequest.getSignatureProfile());

        String signingCertificate = createRemoteSigningRequest.getSigningCertificate();
        String signatureProfile = createRemoteSigningRequest.getSignatureProfile();
        SignatureProductionPlace signatureProductionPlace = createRemoteSigningRequest.getSignatureProductionPlace();
        List<String> roles = createRemoteSigningRequest.getRoles();
        RequestValidator.validateRoles(roles);

        SignatureParameters signatureParameters = RequestTransformer.transformRemoteRequest(signingCertificate, signatureProfile, signatureProductionPlace, roles);
        DataToSignWrapper dataToSignWrapper = signingService.createDataToSign(containerId, signatureParameters);
        DataToSign dataToSign = dataToSignWrapper.getDataToSign();

        CreateHashcodeContainerRemoteSigningResponse response = new CreateHashcodeContainerRemoteSigningResponse();
        response.setGeneratedSignatureId(dataToSignWrapper.getGeneratedSignatureId());
        response.setDataToSign(new String(Base64.getEncoder().encode(dataToSign.getDataToSign())));
        response.setDigestAlgorithm(dataToSign.getDigestAlgorithm().name());

        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_REMOTE_SIGNING_FINISH)
    @PutMapping(value = "/hashcodecontainers/{containerId}/remotesigning/{signatureId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public UpdateHashcodeContainerRemoteSigningResponse finalizeRemoteSignature(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId, @RequestBody UpdateHashcodeContainerRemoteSigningRequest updateRemoteSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureId(signatureId);
        RequestValidator.validateSignatureValue(updateRemoteSigningRequest.getSignatureValue());
        Result result = signingService.finalizeSigning(containerId, signatureId, updateRemoteSigningRequest.getSignatureValue());
        UpdateHashcodeContainerRemoteSigningResponse response = new UpdateHashcodeContainerRemoteSigningResponse();
        response.setResult(result.name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_GET_SIGNATURES_LIST)
    @GetMapping(value = "/hashcodecontainers/{containerId}/signatures", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetHashcodeContainerSignaturesResponse getSignatureList(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        List<ee.openeid.siga.common.model.Signature> signatures = containerService.getSignatures(containerId);
        GetHashcodeContainerSignaturesResponse response = new GetHashcodeContainerSignaturesResponse();
        response.getSignatures().addAll(RequestTransformer.transformSignaturesForResponse(signatures));
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_GET_SIGNATURE)
    @GetMapping(value = "/hashcodecontainers/{containerId}/signatures/{signatureId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerSignatureDetailsResponse getSignature(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureId(signatureId);
        org.digidoc4j.Signature signature = containerService.getSignature(containerId, signatureId);
        return RequestTransformer.transformSignatureToDetails(signature);
    }

    @SigaEventLog(eventName = SigaEventName.HC_GET_DATAFILES_LIST)
    @GetMapping(value = "/hashcodecontainers/{containerId}/datafiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetHashcodeContainerDataFilesResponse getDataFilesList(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        List<ee.openeid.siga.common.model.HashcodeDataFile> dataFiles = containerService.getDataFiles(containerId);
        GetHashcodeContainerDataFilesResponse response = new GetHashcodeContainerDataFilesResponse();
        response.getDataFiles().addAll(RequestTransformer.transformHashcodeDataFilesForResponse(dataFiles));
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_ADD_DATAFILE)
    @PostMapping(value = "/hashcodecontainers/{containerId}/datafiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateHashcodeContainerDataFileResponse addHashcodeContainerDataFile(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashcodeContainerDataFileRequest containerDataFileRequest) {
        RequestValidator.validateContainerId(containerId);
        List<HashcodeDataFile> hashcodeDataFiles = containerDataFileRequest.getDataFiles();
        RequestValidator.validateHashcodeDataFiles(hashcodeDataFiles);

        List<ee.openeid.siga.common.model.HashcodeDataFile> dataFilesForApplication = RequestTransformer.transformHashcodeDataFilesForApplication(hashcodeDataFiles);
        Result result = containerService.addDataFiles(containerId, dataFilesForApplication);
        CreateHashcodeContainerDataFileResponse response = new CreateHashcodeContainerDataFileResponse();
        response.setResult(result.name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_DELETE_DATAFILE)
    @DeleteMapping(value = "/hashcodecontainers/{containerId}/datafiles/{datafileName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeleteHashcodeContainerDataFileResponse deleteHashcodeContainerDataFile(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "datafileName") String datafileName) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateFileName(datafileName);

        Result result = containerService.removeDataFile(containerId, datafileName);
        DeleteHashcodeContainerDataFileResponse response = new DeleteHashcodeContainerDataFileResponse();
        response.setResult(result.name());
        return response;
    }


    @SigaEventLog(eventName = SigaEventName.HC_GET_CONTAINER)
    @GetMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetHashcodeContainerResponse getContainer(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        String container = containerService.getContainer(containerId);
        GetHashcodeContainerResponse response = new GetHashcodeContainerResponse();
        response.setContainer(container);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_DELETE_CONTAINER)
    @DeleteMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeleteHashcodeContainerResponse closeSession(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);
        Result result = containerService.closeSession(containerId);

        connectionRepository.deleteByContainerId(containerId);

        DeleteHashcodeContainerResponse response = new DeleteHashcodeContainerResponse();
        response.setResult(result.name());
        return response;
    }

    @Autowired
    protected void setContainerService(HashcodeContainerService containerService) {
        this.containerService = containerService;
    }

    @Autowired
    protected void setValidationService(HashcodeContainerValidationService validationService) {
        this.validationService = validationService;
    }

    @Autowired
    public void setSigningService(HashcodeContainerSigningService signingService) {
        this.signingService = signingService;
    }

    @Autowired
    public void setConnectionRepository(ConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }
}
