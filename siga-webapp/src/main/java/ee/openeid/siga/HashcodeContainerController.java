package ee.openeid.siga;

import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.common.model.DataToSignWrapper;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerValidationService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.*;
import lombok.RequiredArgsConstructor;
import org.digidoc4j.DataToSign;
import org.digidoc4j.SignatureParameters;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class HashcodeContainerController {
    private final HashcodeContainerService containerService;
    private final HashcodeContainerValidationService validationService;
    private final HashcodeContainerSigningService signingService;
    private final ConnectionRepository connectionRepository;
    private final RequestValidator validator;

    @SigaEventLog(eventName = SigaEventName.HC_CREATE_CONTAINER, logParameters = {@Param(index = 0, fields = {@XPath(name = "no_of_datafiles", xpath = "helper:size(dataFiles)")})}, logReturnObject = {@XPath(name = "container_id", xpath = "containerId")})
    @PostMapping(value = "/hashcodecontainers", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateHashcodeContainerResponse createContainer(@RequestBody CreateHashcodeContainerRequest createContainerRequest) {
        List<HashcodeDataFile> dataFiles = createContainerRequest.getDataFiles();
        validator.validateHashcodeDataFiles(dataFiles);

        String sessionId = containerService.createContainer(RequestTransformer.transformHashcodeDataFilesForApplication(dataFiles));
        CreateHashcodeContainerResponse response = new CreateHashcodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_UPLOAD_CONTAINER, logReturnObject = {@XPath(name = "container_id", xpath = "containerId")})
    @PostMapping(value = "/upload/hashcodecontainers", produces = MediaType.APPLICATION_JSON_VALUE)
    public UploadHashcodeContainerResponse uploadContainer(@RequestBody UploadHashcodeContainerRequest uploadContainerRequest) {
        String container = uploadContainerRequest.getContainer();
        validator.validateFileContent(container);

        String sessionId = containerService.uploadContainer(container);
        UploadHashcodeContainerResponse response = new UploadHashcodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_VALIDATE_CONTAINER)
    @PostMapping(value = "/hashcodecontainers/validationreport", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateHashcodeContainerValidationReportResponse validateContainer(@RequestBody CreateHashcodeContainerValidationReportRequest validationReportRequest) {
        String container = validationReportRequest.getContainer();
        validator.validateFileContent(container);

        SigaUserDetails sigaUserDetails = (SigaUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        ValidationConclusion validationConclusion = validationService.validateContainer(container, sigaUserDetails.getServiceType());
        CreateHashcodeContainerValidationReportResponse response = new CreateHashcodeContainerValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_VALIDATE_CONTAINER_BY_ID)
    @GetMapping(value = "/hashcodecontainers/{containerId}/validationreport", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetHashcodeContainerValidationReportResponse getContainerValidation(@PathVariable(value = "containerId") String containerId) {
        validator.validateContainerId(containerId);

        ValidationConclusion validationConclusion = validationService.validateExistingContainer(containerId);
        GetHashcodeContainerValidationReportResponse response = new GetHashcodeContainerValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_REMOTE_SIGNING_INIT, logParameters = {@Param(index = 1, fields = {@XPath(name = "signature_profile", xpath = "signatureProfile")})})
    @PostMapping(value = "/hashcodecontainers/{containerId}/remotesigning", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateHashcodeContainerRemoteSigningResponse prepareRemoteSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashcodeContainerRemoteSigningRequest createRemoteSigningRequest) {
        validator.validateContainerId(containerId);

        String signingCertificate = createRemoteSigningRequest.getSigningCertificate();
        validator.validateSigningCertificate(signingCertificate);
        X509Certificate certificate = RequestTransformer.transformCertificate(signingCertificate);
        validator.validateRemoteSigning(certificate, createRemoteSigningRequest.getSignatureProfile());

        String signatureProfile = createRemoteSigningRequest.getSignatureProfile();
        SignatureProductionPlace signatureProductionPlace = createRemoteSigningRequest.getSignatureProductionPlace();
        List<String> roles = createRemoteSigningRequest.getRoles();
        validator.validateRoles(roles);

        SignatureParameters signatureParameters = RequestTransformer.transformRemoteRequest(certificate, signatureProfile, signatureProductionPlace, roles);
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
        validator.validateContainerId(containerId);
        validator.validateSignatureId(signatureId);
        validator.validateSignatureValue(updateRemoteSigningRequest.getSignatureValue());
        Result result = signingService.finalizeSigning(containerId, signatureId, updateRemoteSigningRequest.getSignatureValue());
        UpdateHashcodeContainerRemoteSigningResponse response = new UpdateHashcodeContainerRemoteSigningResponse();
        response.setResult(result.name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_GET_SIGNATURES_LIST)
    @GetMapping(value = "/hashcodecontainers/{containerId}/signatures", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetHashcodeContainerSignaturesResponse getSignatureList(@PathVariable(value = "containerId") String containerId) {
        validator.validateContainerId(containerId);

        List<ee.openeid.siga.common.model.Signature> signatures = containerService.getSignatures(containerId);
        GetHashcodeContainerSignaturesResponse response = new GetHashcodeContainerSignaturesResponse();
        response.getSignatures().addAll(RequestTransformer.transformSignaturesForResponse(signatures));
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_GET_SIGNATURE)
    @GetMapping(value = "/hashcodecontainers/{containerId}/signatures/{signatureId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerSignatureDetailsResponse getSignature(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId) {
        validator.validateContainerId(containerId);
        validator.validateSignatureId(signatureId);
        org.digidoc4j.Signature signature = containerService.getSignature(containerId, signatureId);
        return RequestTransformer.transformSignatureToDetails(signature);
    }

    @SigaEventLog(eventName = SigaEventName.HC_GET_DATAFILES_LIST)
    @GetMapping(value = "/hashcodecontainers/{containerId}/datafiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetHashcodeContainerDataFilesResponse getDataFilesList(@PathVariable(value = "containerId") String containerId) {
        validator.validateContainerId(containerId);

        List<ee.openeid.siga.common.model.HashcodeDataFile> dataFiles = containerService.getDataFiles(containerId);
        GetHashcodeContainerDataFilesResponse response = new GetHashcodeContainerDataFilesResponse();
        response.getDataFiles().addAll(RequestTransformer.transformHashcodeDataFilesForResponse(dataFiles));
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_ADD_DATAFILE)
    @PostMapping(value = "/hashcodecontainers/{containerId}/datafiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateHashcodeContainerDataFileResponse addHashcodeContainerDataFile(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashcodeContainerDataFileRequest containerDataFileRequest) {
        validator.validateContainerId(containerId);
        List<HashcodeDataFile> hashcodeDataFiles = containerDataFileRequest.getDataFiles();
        validator.validateHashcodeDataFiles(hashcodeDataFiles);

        List<ee.openeid.siga.common.model.HashcodeDataFile> dataFilesForApplication = RequestTransformer.transformHashcodeDataFilesForApplication(hashcodeDataFiles);
        Result result = containerService.addDataFiles(containerId, dataFilesForApplication);
        CreateHashcodeContainerDataFileResponse response = new CreateHashcodeContainerDataFileResponse();
        response.setResult(result.name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_DELETE_DATAFILE)
    @DeleteMapping(value = "/hashcodecontainers/{containerId}/datafiles/{datafileName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeleteHashcodeContainerDataFileResponse deleteHashcodeContainerDataFile(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "datafileName") String datafileName) {
        validator.validateContainerId(containerId);
        validator.validateFileName(datafileName);

        Result result = containerService.removeDataFile(containerId, datafileName);
        DeleteHashcodeContainerDataFileResponse response = new DeleteHashcodeContainerDataFileResponse();
        response.setResult(result.name());
        return response;
    }


    @SigaEventLog(eventName = SigaEventName.HC_GET_CONTAINER)
    @GetMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetHashcodeContainerResponse getContainer(@PathVariable(value = "containerId") String containerId) {
        validator.validateContainerId(containerId);

        String container = containerService.getContainer(containerId);
        GetHashcodeContainerResponse response = new GetHashcodeContainerResponse();
        response.setContainer(container);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_DELETE_CONTAINER)
    @DeleteMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeleteHashcodeContainerResponse closeSession(@PathVariable(value = "containerId") String containerId) {
        validator.validateContainerId(containerId);
        Result result = containerService.closeSession(containerId);

        connectionRepository.deleteByContainerId(containerId);

        DeleteHashcodeContainerResponse response = new DeleteHashcodeContainerResponse();
        response.setResult(result.name());
        return response;
    }

}
