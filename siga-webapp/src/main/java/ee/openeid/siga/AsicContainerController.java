package ee.openeid.siga;

import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.common.model.ContainerInfo;
import ee.openeid.siga.common.model.DataToSignWrapper;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.Result;
import ee.openeid.siga.common.model.SigningChallenge;
import ee.openeid.siga.common.event.Param;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.event.XPath;
import ee.openeid.siga.service.signature.container.asic.AsicContainerService;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.service.signature.container.asic.AsicContainerValidationService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.*;
import org.digidoc4j.DataToSign;
import org.digidoc4j.SignatureParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
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
@Profile("datafileContainer")
public class AsicContainerController {

    private AsicContainerService containerService;
    private AsicContainerValidationService validationService;
    private AsicContainerSigningService signingService;
    private ConnectionRepository connectionRepository;

    @SigaEventLog(eventName = SigaEventName.CREATE_CONTAINER, logParameters = {@Param(index = 0, fields = {@XPath(name = "no_of_datafiles", xpath = "helper:size(dataFiles)")})}, logReturnObject = {@XPath(name = "container_id", xpath = "containerId")})
    @PostMapping(value = "/containers", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateContainerResponse createContainer(@RequestBody CreateContainerRequest createContainerRequest) {
        List<DataFile> dataFiles = createContainerRequest.getDataFiles();
        String containerName = createContainerRequest.getContainerName();
        RequestValidator.validateDataFiles(dataFiles);
        RequestValidator.validateContainerName(containerName);

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
        RequestValidator.validateFileContent(container);
        RequestValidator.validateContainerName(containerName);
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
        RequestValidator.validateContainerName(containerName);
        RequestValidator.validateFileContent(container);

        ValidationConclusion validationConclusion = validationService.validateContainer(containerName, container);
        CreateContainerValidationReportResponse response = new CreateContainerValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.VALIDATE_CONTAINER_BY_ID)
    @GetMapping(value = "/containers/{containerId}/validationreport", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerValidationReportResponse getContainerValidation(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        ValidationConclusion validationConclusion = validationService.validateExistingContainer(containerId);
        GetContainerValidationReportResponse response = new GetContainerValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }


    @SigaEventLog(eventName = SigaEventName.REMOTE_SIGNING_INIT, logParameters = {@Param(index = 1, fields = {@XPath(name = "signature_profile", xpath = "signatureProfile")})})
    @PostMapping(value = "/containers/{containerId}/remotesigning", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateContainerRemoteSigningResponse prepareRemoteSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateContainerRemoteSigningRequest createRemoteSigningRequest) {
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
        CreateContainerRemoteSigningResponse response = new CreateContainerRemoteSigningResponse();
        response.setGeneratedSignatureId(dataToSignWrapper.getGeneratedSignatureId());
        response.setDataToSign(new String(Base64.getEncoder().encode(dataToSign.getDataToSign())));
        response.setDigestAlgorithm(dataToSign.getDigestAlgorithm().name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.REMOTE_SIGNING_FINISH)
    @PutMapping(value = "/containers/{containerId}/remotesigning/{signatureId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public UpdateContainerRemoteSigningResponse finalizeRemoteSignature(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId, @RequestBody UpdateContainerRemoteSigningRequest updateRemoteSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureId(signatureId);
        RequestValidator.validateSignatureValue(updateRemoteSigningRequest.getSignatureValue());
        Result result = signingService.finalizeSigning(containerId, signatureId, updateRemoteSigningRequest.getSignatureValue());
        UpdateContainerRemoteSigningResponse response = new UpdateContainerRemoteSigningResponse();
        response.setResult(result.name());
        return response;
    }


    @SigaEventLog(eventName = SigaEventName.MOBILE_ID_SIGNING_INIT)
    @PostMapping(value = "/containers/{containerId}/mobileidsigning", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateContainerMobileIdSigningResponse prepareMobileIdSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateContainerMobileIdSigningRequest createMobileIdSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureProfile(createMobileIdSigningRequest.getSignatureProfile());

        List<String> roles = createMobileIdSigningRequest.getRoles();
        RequestValidator.validateRoles(roles);
        String signatureProfile = createMobileIdSigningRequest.getSignatureProfile();
        SignatureProductionPlace signatureProductionPlace = createMobileIdSigningRequest.getSignatureProductionPlace();

        MobileIdInformation mobileIdInformation = getMobileIdInformation(createMobileIdSigningRequest);
        SignatureParameters signatureParameters = RequestTransformer.transformSignatureParameters(signatureProfile, signatureProductionPlace, roles);
        RequestValidator.validateMobileIdInformation(mobileIdInformation);

        SigningChallenge challenge = signingService.startMobileIdSigning(containerId, mobileIdInformation, signatureParameters);

        CreateContainerMobileIdSigningResponse response = new CreateContainerMobileIdSigningResponse();
        response.setChallengeId(challenge.getChallengeId());
        response.setGeneratedSignatureId(challenge.getGeneratedSignatureId());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.MOBILE_ID_SIGNING_STATUS)
    @GetMapping(value = "/containers/{containerId}/mobileidsigning/{signatureId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerMobileIdSigningStatusResponse getMobileSigningStatus(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureId(signatureId);
        MobileIdInformation mobileIdInformation = RequestTransformer.transformMobileIdInformation();
        String status = signingService.processMobileStatus(containerId, signatureId, mobileIdInformation);

        GetContainerMobileIdSigningStatusResponse response = new GetContainerMobileIdSigningStatusResponse();
        response.setMidStatus(status);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.GET_SIGNATURES_LIST)
    @GetMapping(value = "/containers/{containerId}/signatures", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerSignaturesResponse getSignatureList(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        List<ee.openeid.siga.common.model.Signature> signatures = containerService.getSignatures(containerId);
        GetContainerSignaturesResponse response = new GetContainerSignaturesResponse();
        response.getSignatures().addAll(RequestTransformer.transformSignaturesForResponse(signatures));
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.GET_SIGNATURE)
    @GetMapping(value = "/containers/{containerId}/signatures/{signatureId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerSignatureDetailsResponse getSignature(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureId(signatureId);
        org.digidoc4j.Signature signature = containerService.getSignature(containerId, signatureId);
        return RequestTransformer.transformSignatureToDetails(signature);
    }

    @SigaEventLog(eventName = SigaEventName.GET_DATAFILES_LIST)
    @GetMapping(value = "/containers/{containerId}/datafiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerDataFilesResponse getDataFilesList(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        List<ee.openeid.siga.common.model.DataFile> dataFiles = containerService.getDataFiles(containerId);
        GetContainerDataFilesResponse response = new GetContainerDataFilesResponse();
        response.getDataFiles().addAll(RequestTransformer.transformDataFilesForResponse(dataFiles));
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.ADD_DATAFILE)
    @PostMapping(value = "/containers/{containerId}/datafiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateContainerDataFileResponse addContainerDataFile(@PathVariable(value = "containerId") String containerId, @RequestBody CreateContainerDataFileRequest containerDataFileRequest) {
        RequestValidator.validateContainerId(containerId);
        List<DataFile> dataFiles = containerDataFileRequest.getDataFiles();
        RequestValidator.validateDataFiles(dataFiles);

        List<ee.openeid.siga.common.model.DataFile> dataFilesForApplication = RequestTransformer.transformDataFilesForApplication(dataFiles);
        Result result = containerService.addDataFiles(containerId, dataFilesForApplication);
        CreateContainerDataFileResponse response = new CreateContainerDataFileResponse();
        response.setResult(result.name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.DELETE_DATAFILE)
    @DeleteMapping(value = "/containers/{containerId}/datafiles/{datafileName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeleteContainerDataFileResponse deleteContainerDataFile(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "datafileName") String datafileName) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateFileName(datafileName);

        Result result = containerService.removeDataFile(containerId, datafileName);
        DeleteContainerDataFileResponse response = new DeleteContainerDataFileResponse();
        response.setResult(result.name());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.GET_CONTAINER)
    @GetMapping(value = "/containers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerResponse getContainer(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        ContainerInfo containerInfo = containerService.getContainer(containerId);
        GetContainerResponse response = new GetContainerResponse();
        response.setContainer(containerInfo.getContainer());
        response.setContainerName(containerInfo.getContainerName());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.DELETE_CONTAINER)
    @DeleteMapping(value = "/containers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DeleteContainerResponse closeSession(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);
        String result = containerService.closeSession(containerId);

        connectionRepository.deleteByContainerId(containerId);

        DeleteContainerResponse response = new DeleteContainerResponse();
        response.setResult(result);
        return response;
    }

    private MobileIdInformation getMobileIdInformation(CreateContainerMobileIdSigningRequest createMobileIdSigningRequest) {
        String language = createMobileIdSigningRequest.getLanguage();
        String messageToDisplay = createMobileIdSigningRequest.getMessageToDisplay();
        String phoneNo = createMobileIdSigningRequest.getPhoneNo();
        String personIdentifier = createMobileIdSigningRequest.getPersonIdentifier();
        return RequestTransformer.transformMobileIdInformation(language, messageToDisplay, personIdentifier, phoneNo);
    }

    @Autowired
    public void setContainerService(AsicContainerService containerService) {
        this.containerService = containerService;
    }

    @Autowired
    public void setValidationService(AsicContainerValidationService validationService) {
        this.validationService = validationService;
    }

    @Autowired
    public void setSigningService(AsicContainerSigningService signingService) {
        this.signingService = signingService;
    }

    @Autowired
    public void setConnectionRepository(ConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }
}
