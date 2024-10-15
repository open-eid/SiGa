package ee.openeid.siga;

import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.common.model.ContainerInfo;
import ee.openeid.siga.common.model.DataToSignWrapper;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.service.signature.container.asic.AsicContainerService;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.service.signature.container.asic.AsicContainerValidationService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.AugmentContainerSignaturesResponse;
import ee.openeid.siga.webapp.json.CreateContainerDataFileRequest;
import ee.openeid.siga.webapp.json.CreateContainerDataFileResponse;
import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningRequest;
import ee.openeid.siga.webapp.json.CreateContainerRemoteSigningResponse;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.CreateContainerResponse;
import ee.openeid.siga.webapp.json.CreateContainerValidationReportRequest;
import ee.openeid.siga.webapp.json.CreateContainerValidationReportResponse;
import ee.openeid.siga.webapp.json.DataFile;
import ee.openeid.siga.webapp.json.DeleteContainerDataFileResponse;
import ee.openeid.siga.webapp.json.DeleteContainerResponse;
import ee.openeid.siga.webapp.json.GetContainerDataFilesResponse;
import ee.openeid.siga.webapp.json.GetContainerResponse;
import ee.openeid.siga.webapp.json.GetContainerSignatureDetailsResponse;
import ee.openeid.siga.webapp.json.GetContainerSignaturesResponse;
import ee.openeid.siga.webapp.json.GetContainerTimestampsResponse;
import ee.openeid.siga.webapp.json.GetContainerValidationReportResponse;
import ee.openeid.siga.webapp.json.SignatureProductionPlace;
import ee.openeid.siga.webapp.json.UpdateContainerRemoteSigningRequest;
import ee.openeid.siga.webapp.json.UpdateContainerRemoteSigningResponse;
import ee.openeid.siga.webapp.json.UploadContainerRequest;
import ee.openeid.siga.webapp.json.UploadContainerResponse;
import ee.openeid.siga.webapp.json.ValidationConclusion;
import lombok.RequiredArgsConstructor;
import org.digidoc4j.DataToSign;
import org.digidoc4j.SignatureParameters;
import org.digidoc4j.Timestamp;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.OptionalInt;

@RestController
@Profile("datafileContainer")
@RequiredArgsConstructor
public class AsicContainerController {

    private final AsicContainerService containerService;
    private final AsicContainerValidationService validationService;
    private final AsicContainerSigningService signingService;
    private final ConnectionRepository connectionRepository;
    private final RequestValidator validator;

    @SigaEventLog(eventName = SigaEventName.CREATE_CONTAINER, logParameters = {@Param(index = 0, fields = {@XPath(name = "no_of_datafiles", xpath = "helper:size(dataFiles)")})}, logReturnObject = {@XPath(name = "container_id", xpath = "containerId")})
    @PostMapping(value = "/containers", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateContainerResponse createContainer(@RequestBody CreateContainerRequest createContainerRequest) {
        List<DataFile> dataFiles = createContainerRequest.getDataFiles();
        String containerName = createContainerRequest.getContainerName();
        validator.validateDataFiles(dataFiles);
        validator.validateContainerName(containerName);

        String sessionId = containerService.createContainer(containerName, RequestTransformer.transformDataFilesForApplication(dataFiles));
        CreateContainerResponse response = new CreateContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.UPLOAD_CONTAINER, logReturnObject = {@XPath(name = "container_id", xpath = "containerId")})
    @PostMapping(value = "/upload/containers", produces = MediaType.APPLICATION_JSON_VALUE)
    public UploadContainerResponse uploadContainer(@RequestBody UploadContainerRequest uploadContainerRequest) {
        String container = uploadContainerRequest.getContainer();
        String containerName = uploadContainerRequest.getContainerName();
        validator.validateFileContent(container);
        validator.validateContainerName(containerName);
        String sessionId = containerService.uploadContainer(containerName, container);
        UploadContainerResponse response = new UploadContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.VALIDATE_CONTAINER)
    @PostMapping(value = "/containers/validationreport", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateContainerValidationReportResponse validateContainer(@RequestBody CreateContainerValidationReportRequest validationReportRequest) {
        String container = validationReportRequest.getContainer();
        String containerName = validationReportRequest.getContainerName();
        validator.validateContainerName(containerName);
        validator.validateFileContent(container);

        ValidationConclusion validationConclusion = validationService.validateContainer(containerName, container);
        CreateContainerValidationReportResponse response = new CreateContainerValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.VALIDATE_CONTAINER_BY_ID)
    @GetMapping(value = "/containers/{containerId}/validationreport", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerValidationReportResponse getContainerValidation(@PathVariable(value = "containerId") String containerId) {
        validator.validateContainerId(containerId);

        ValidationConclusion validationConclusion = validationService.validateExistingContainer(containerId);
        GetContainerValidationReportResponse response = new GetContainerValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }


    @SigaEventLog(eventName = SigaEventName.REMOTE_SIGNING_INIT, logParameters = {@Param(index = 1, fields = {@XPath(name = "signature_profile", xpath = "signatureProfile")})})
    @PostMapping(value = "/containers/{containerId}/remotesigning", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateContainerRemoteSigningResponse prepareRemoteSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateContainerRemoteSigningRequest createRemoteSigningRequest) {
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
        CreateContainerRemoteSigningResponse response = new CreateContainerRemoteSigningResponse();
        response.setGeneratedSignatureId(dataToSignWrapper.getGeneratedSignatureId());
        response.setDataToSign(new String(Base64.getEncoder().encode(dataToSign.getDataToSign())));
        response.setDigestAlgorithm(dataToSign.getDigestAlgorithm().name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.REMOTE_SIGNING_FINISH)
    @PutMapping(value = "/containers/{containerId}/remotesigning/{signatureId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public UpdateContainerRemoteSigningResponse finalizeRemoteSignature(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId, @RequestBody UpdateContainerRemoteSigningRequest updateRemoteSigningRequest) {
        validator.validateContainerId(containerId);
        validator.validateSignatureId(signatureId);
        validator.validateSignatureValue(updateRemoteSigningRequest.getSignatureValue());
        Result result = signingService.finalizeSigning(containerId, signatureId, updateRemoteSigningRequest.getSignatureValue());
        UpdateContainerRemoteSigningResponse response = new UpdateContainerRemoteSigningResponse();
        response.setResult(result.name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.GET_SIGNATURES_LIST)
    @GetMapping(value = "/containers/{containerId}/signatures", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerSignaturesResponse getSignatureList(@PathVariable(value = "containerId") String containerId) {
        validator.validateContainerId(containerId);

        List<ee.openeid.siga.common.model.Signature> signatures = containerService.getSignatures(containerId);
        GetContainerSignaturesResponse response = new GetContainerSignaturesResponse();
        response.getSignatures().addAll(RequestTransformer.transformSignaturesForResponse(signatures));
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.GET_SIGNATURE)
    @GetMapping(value = "/containers/{containerId}/signatures/{signatureId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerSignatureDetailsResponse getSignature(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId) {
        validator.validateContainerId(containerId);
        validator.validateSignatureId(signatureId);
        org.digidoc4j.Signature signature = containerService.getSignature(containerId, signatureId);
        return RequestTransformer.transformSignatureToDetails(signature);
    }

    @SigaEventLog(eventName = SigaEventName.GET_TIMESTAMPS_LIST)
    @GetMapping(value = "/containers/{containerId}/timestamps", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerTimestampsResponse getTimestampList(@PathVariable(value = "containerId") String containerId) {
        validator.validateContainerId(containerId);

        List<Timestamp> timestamps = containerService.getTimestamps(containerId);
        GetContainerTimestampsResponse response = new GetContainerTimestampsResponse();
        response.getTimestamps().addAll(RequestTransformer.transformTimestampsForResponse(timestamps));
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.GET_DATAFILES_LIST)
    @GetMapping(value = "/containers/{containerId}/datafiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerDataFilesResponse getDataFilesList(@PathVariable(value = "containerId") String containerId) {
        validator.validateContainerId(containerId);

        List<ee.openeid.siga.common.model.DataFile> dataFiles = containerService.getDataFiles(containerId);
        GetContainerDataFilesResponse response = new GetContainerDataFilesResponse();
        response.getDataFiles().addAll(RequestTransformer.transformDataFilesForResponse(dataFiles));
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.ADD_DATAFILE)
    @PostMapping(value = "/containers/{containerId}/datafiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateContainerDataFileResponse addContainerDataFile(@PathVariable(value = "containerId") String containerId, @RequestBody CreateContainerDataFileRequest containerDataFileRequest) {
        validator.validateContainerId(containerId);
        List<DataFile> dataFiles = containerDataFileRequest.getDataFiles();
        validator.validateDataFiles(dataFiles);

        List<ee.openeid.siga.common.model.DataFile> dataFilesForApplication = RequestTransformer.transformDataFilesForApplication(dataFiles);
        Result result = containerService.addDataFiles(containerId, dataFilesForApplication);
        CreateContainerDataFileResponse response = new CreateContainerDataFileResponse();
        response.setResult(result.name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.DELETE_DATAFILE)
    @DeleteMapping(value = "/containers/{containerId}/datafiles/{datafileName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeleteContainerDataFileResponse deleteContainerDataFile(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "datafileName") String datafileName) {
        validator.validateContainerId(containerId);
        validator.validateFileName(datafileName);

        Result result = containerService.removeDataFile(containerId, datafileName);
        DeleteContainerDataFileResponse response = new DeleteContainerDataFileResponse();
        response.setResult(result.name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.GET_CONTAINER)
    @GetMapping(value = "/containers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerResponse getContainer(@PathVariable(value = "containerId") String containerId) {
        validator.validateContainerId(containerId);

        ContainerInfo containerInfo = containerService.getContainer(containerId);
        GetContainerResponse response = new GetContainerResponse();
        response.setContainer(containerInfo.getContainer());
        response.setContainerName(containerInfo.getContainerName());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.DELETE_CONTAINER)
    @DeleteMapping(value = "/containers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeleteContainerResponse closeSession(@PathVariable(value = "containerId") String containerId) {
        validator.validateContainerId(containerId);
        String result = containerService.closeSession(containerId);

        findCurrentSessionServiceId().ifPresent(
                serviceId -> connectionRepository.deleteByContainerIdAndServiceId(containerId, serviceId)
        );

        DeleteContainerResponse response = new DeleteContainerResponse();
        response.setResult(result);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.AUGMENT_SIGNATURES)
    @PutMapping(value = "/containers/{containerId}/augmentation", produces = MediaType.APPLICATION_JSON_VALUE)
    public AugmentContainerSignaturesResponse augmentContainerSignatures(@PathVariable(value = "containerId") String containerId) {
        validator.validateContainerId(containerId);

        Result result = containerService.augmentContainer(containerId);
        AugmentContainerSignaturesResponse response = new AugmentContainerSignaturesResponse();
        response.setResult(result.name());
        return response;
    }

    private OptionalInt findCurrentSessionServiceId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof SigaUserDetails userDetails) {
            return OptionalInt.of(userDetails.getServiceId());
        } else {
            return OptionalInt.empty();
        }
    }
}
