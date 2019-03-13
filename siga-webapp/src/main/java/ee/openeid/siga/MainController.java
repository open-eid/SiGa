package ee.openeid.siga;

import ee.openeid.siga.common.MobileIdInformation;
import ee.openeid.siga.service.signature.DetachedDataFileContainerService;
import ee.openeid.siga.service.signature.DetachedDataFileContainerSigningService;
import ee.openeid.siga.service.signature.DetachedDataFileContainerValidationService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.*;
import org.digidoc4j.DataToSign;
import org.digidoc4j.SignatureParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;

@RestController
public class MainController {

    private DetachedDataFileContainerService containerService;
    private DetachedDataFileContainerValidationService validationService;
    private DetachedDataFileContainerSigningService signingService;

    @RequestMapping(value = "/hashcodecontainers", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashcodeContainerResponse createContainer(@RequestBody CreateHashcodeContainerRequest createContainerRequest) {
        List<HashcodeDataFile> dataFiles = createContainerRequest.getDataFiles();
        RequestValidator.validateHashcodeDataFiles(dataFiles);

        String sessionId = containerService.createContainer(dataFiles);
        CreateHashcodeContainerResponse response = new CreateHashcodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @RequestMapping(value = "/upload/hashcodecontainers", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public UploadHashcodeContainerResponse uploadContainer(@RequestBody UploadHashcodeContainerRequest uploadContainerRequest) {
        String container = uploadContainerRequest.getContainer();
        RequestValidator.validateFileContent(container);

        String sessionId = containerService.uploadContainer(container);
        UploadHashcodeContainerResponse response = new UploadHashcodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/validationreport", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashcodeValidationReportResponse validateContainer(@RequestBody CreateHashcodeValidationReportRequest validationReportRequest) {
        String container = validationReportRequest.getContainer();
        RequestValidator.validateFileContent(container);

        ValidationConclusion validationConclusion = validationService.validateContainer(container);
        CreateHashcodeValidationReportResponse response = new CreateHashcodeValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/validationreport", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashcodeValidationReportResponse getContainerValidation(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        ValidationConclusion validationConclusion = validationService.validateExistingContainer(containerId);
        GetHashcodeValidationReportResponse response = new GetHashcodeValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/remotesigning", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashcodeRemoteSigningResponse prepareRemoteSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashcodeRemoteSigningRequest createRemoteSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateRemoteSigning(createRemoteSigningRequest.getSigningCertificate(), createRemoteSigningRequest.getSignatureProfile());

        SignatureParameters signatureParameters = RequestTransformer.transformRemoteRequest(createRemoteSigningRequest);
        DataToSign dataToSign = signingService.createDataToSign(containerId, signatureParameters);

        CreateHashcodeRemoteSigningResponse response = new CreateHashcodeRemoteSigningResponse();
        response.setDataToSign(new String(Base64.getEncoder().encode(dataToSign.getDataToSign())));
        response.setDigestAlgorithm(dataToSign.getDigestAlgorithm().name());
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/remotesigning", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.PUT)
    public UpdateHashcodeRemoteSigningResponse finalizeRemoteSignature(@PathVariable(value = "containerId") String containerId, @RequestBody UpdateHashcodeRemoteSigningRequest updateRemoteSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureValue(updateRemoteSigningRequest.getSignatureValue());
        String result = signingService.finalizeSigning(containerId, updateRemoteSigningRequest.getSignatureValue());
        UpdateHashcodeRemoteSigningResponse response = new UpdateHashcodeRemoteSigningResponse();
        response.setResult(result);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/mobileidsigning", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashcodeMobileIdSigningResponse prepareMobileIdSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashcodeMobileIdSigningRequest createMobileIdSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureProfile(createMobileIdSigningRequest.getSignatureProfile());
        MobileIdInformation mobileIdInformation = RequestTransformer.transformMobileIdInformation(createMobileIdSigningRequest);
        SignatureParameters signatureParameters = RequestTransformer.transformMobileIdSignatureParameters(createMobileIdSigningRequest);
        RequestValidator.validateMobileIdInformation(mobileIdInformation);

        String challengeId = signingService.startMobileIdSigning(containerId, mobileIdInformation, signatureParameters);

        CreateHashcodeMobileIdSigningResponse response = new CreateHashcodeMobileIdSigningResponse();
        response.setChallengeId(challengeId);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/mobileidsigning/status", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashcodeMobileIdSigningStatusResponse getMobileSigningStatus(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        String status = signingService.processMobileStatus(containerId);

        GetHashcodeMobileIdSigningStatusResponse response = new GetHashcodeMobileIdSigningStatusResponse();
        response.setMidStatus(status);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/signatures", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashcodeSignaturesResponse getSignatureList(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        List<Signature> signatures = containerService.getSignatures(containerId);
        GetHashcodeSignaturesResponse response = new GetHashcodeSignaturesResponse();
        response.getSignatures().addAll(signatures);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashcodeContainerResponse getContainer(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        String container = containerService.getContainer(containerId);
        GetHashcodeContainerResponse response = new GetHashcodeContainerResponse();
        response.setContainer(container);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.DELETE)
    public DeleteHashcodeContainerResponse closeSession(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);
        String result = containerService.closeSession(containerId);
        DeleteHashcodeContainerResponse response = new DeleteHashcodeContainerResponse();
        response.setResult(result);
        return response;
    }

    @Autowired
    protected void setContainerService(DetachedDataFileContainerService containerService) {
        this.containerService = containerService;
    }

    @Autowired
    protected void setValidationService(DetachedDataFileContainerValidationService validationService) {
        this.validationService = validationService;
    }

    @Autowired
    public void setSigningService(DetachedDataFileContainerSigningService signingService) {
        this.signingService = signingService;
    }
}
