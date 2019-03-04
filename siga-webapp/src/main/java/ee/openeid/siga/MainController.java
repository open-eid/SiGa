package ee.openeid.siga;

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
    public CreateHashCodeContainerResponse createContainer(@RequestBody CreateHashCodeContainerRequest createContainerRequest) {
        List<HashCodeDataFile> dataFiles = createContainerRequest.getDataFiles();
        RequestValidator.validateHashCodeDataFiles(dataFiles);

        String sessionId = containerService.createContainer(dataFiles);
        CreateHashCodeContainerResponse response = new CreateHashCodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @RequestMapping(value = "/upload/hashcodecontainers", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public UploadHashCodeContainerResponse uploadContainer(@RequestBody UploadHashCodeContainerRequest uploadContainerRequest) {
        String container = uploadContainerRequest.getContainer();
        RequestValidator.validateFileContent(container);

        String sessionId = containerService.uploadContainer(container);
        UploadHashCodeContainerResponse response = new UploadHashCodeContainerResponse();
        response.setContainerId(sessionId);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/validationreport", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashCodeValidationReportResponse validateContainer(@RequestBody CreateHashCodeValidationReportRequest validationReportRequest) {
        String container = validationReportRequest.getContainer();
        RequestValidator.validateFileContent(container);

        ValidationConclusion validationConclusion = validationService.validateContainer(container);
        CreateHashCodeValidationReportResponse response = new CreateHashCodeValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/validationreport", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashCodeValidationReportResponse getContainerValidation(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        ValidationConclusion validationConclusion = validationService.validateExistingContainer(containerId);
        GetHashCodeValidationReportResponse response = new GetHashCodeValidationReportResponse();
        response.setValidationConclusion(validationConclusion);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/remotesigning", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateHashCodeRemoteSigningResponse prepareRemoteSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashCodeRemoteSigningRequest createRemoteSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateRemoteSigning(createRemoteSigningRequest.getSigningCertificate(), createRemoteSigningRequest.getSignatureProfile());

        SignatureParameters signatureParameters = RequestTransformer.transformRemoteRequest(createRemoteSigningRequest);
        DataToSign dataToSign = signingService.createDataToSign(containerId, signatureParameters);

        CreateHashCodeRemoteSigningResponse response = new CreateHashCodeRemoteSigningResponse();
        String dataToSignBase64 = new String(Base64.getEncoder().encode(dataToSign.getDataToSign()));
        response.setDataToSign(dataToSignBase64);
        response.setDigestAlgorithm(dataToSign.getDigestAlgorithm().name());
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}/remotesigning", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.PUT)
    public UpdateHashCodeRemoteSigningResponse finalizeRemoteSignature(@PathVariable(value = "containerId") String containerId, @RequestBody UpdateHashCodeRemoteSigningRequest updateRemoteSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureValue(updateRemoteSigningRequest.getSignatureValue());
        String result = signingService.finalizeSigning(containerId, updateRemoteSigningRequest.getSignatureValue());
        UpdateHashCodeRemoteSigningResponse response = new UpdateHashCodeRemoteSigningResponse();
        response.setResult(result);
        return response;
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

        List<Signature> signatures = containerService.getSignatures(containerId);
        GetHashCodeSignaturesResponse response = new GetHashCodeSignaturesResponse();
        response.getSignatures().addAll(signatures);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetHashCodeContainerResponse getContainer(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);

        String container = containerService.getContainer(containerId);
        GetHashCodeContainerResponse response = new GetHashCodeContainerResponse();
        response.setContainer(container);
        return response;
    }

    @RequestMapping(value = "/hashcodecontainers/{containerId}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.DELETE)
    public DeleteHashCodeContainerResponse closeSession(@PathVariable(value = "containerId") String containerId) {
        RequestValidator.validateContainerId(containerId);
        String result = containerService.closeSession(containerId);
        DeleteHashCodeContainerResponse response = new DeleteHashCodeContainerResponse();
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
