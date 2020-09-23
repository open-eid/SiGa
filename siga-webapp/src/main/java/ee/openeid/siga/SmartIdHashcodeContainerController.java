package ee.openeid.siga;


import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.model.CertificateStatus;
import ee.openeid.siga.common.model.SigningChallenge;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerSmartIdCertificateChoiceRequest;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerSmartIdCertificateChoiceResponse;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerSmartIdSigningRequest;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerSmartIdSigningResponse;
import ee.openeid.siga.webapp.json.GetHashcodeContainerSmartIdCertificateChoiceStatusResponse;
import ee.openeid.siga.webapp.json.GetHashcodeContainerSmartIdSigningStatusResponse;
import ee.openeid.siga.webapp.json.SignatureProductionPlace;
import org.digidoc4j.SignatureParameters;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("smartId")
public class SmartIdHashcodeContainerController {

    private HashcodeContainerSigningService signingService;
    private final RequestValidator validator;

    public SmartIdHashcodeContainerController(HashcodeContainerSigningService signingService, RequestValidator validator) {
        this.signingService = signingService;
        this.validator = validator;
    }

    @SigaEventLog(eventName = SigaEventName.HC_SMART_ID_CERTIFICATE_CHOICE_INIT)
    @PostMapping(value = "/hashcodecontainers/{containerId}/smartidsigning/certificatechoice", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateHashcodeContainerSmartIdCertificateChoiceResponse createHashcodeContainerSmartIdCertificateChoice(
            @PathVariable(value = "containerId") String containerId,
            @RequestBody CreateHashcodeContainerSmartIdCertificateChoiceRequest certificateChoiceRequest) {

        SmartIdInformation smartIdInformation = getSmartIdInformation(certificateChoiceRequest);
        validator.validateContainerId(containerId);
        validator.validateSmartIdInformationForCertChoice(smartIdInformation);

        String certificateId = signingService.initSmartIdCertificateChoice(containerId, smartIdInformation);
        CreateHashcodeContainerSmartIdCertificateChoiceResponse response = new CreateHashcodeContainerSmartIdCertificateChoiceResponse();
        response.setGeneratedCertificateId(certificateId);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_SMART_ID_CERTIFICATE_CHOICE_STATUS)
    @GetMapping(value = "/hashcodecontainers/{containerId}/smartidsigning/certificatechoice/{certificateId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetHashcodeContainerSmartIdCertificateChoiceStatusResponse getSmartIdCertificateChoiceStatus(
            @PathVariable(value = "containerId") String containerId,
            @PathVariable(value = "certificateId") String certificateId) {

        validator.validateContainerId(containerId);
        validator.validateCertificateId(certificateId);

        CertificateStatus status = signingService.processSmartIdCertificateStatus(containerId, certificateId);
        GetHashcodeContainerSmartIdCertificateChoiceStatusResponse response = new GetHashcodeContainerSmartIdCertificateChoiceStatusResponse();
        response.setSidStatus(status.getStatus());
        response.setDocumentNumber(status.getDocumentNumber());
        return response;
    }


    @SigaEventLog(eventName = SigaEventName.HC_SMART_ID_SIGNING_INIT)
    @PostMapping(value = "/hashcodecontainers/{containerId}/smartidsigning", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateHashcodeContainerSmartIdSigningResponse createHashcodeContainerSmartIdSigning(
            @PathVariable(value = "containerId") String containerId,
            @RequestBody CreateHashcodeContainerSmartIdSigningRequest createSmartIdSigningRequest) {
        validator.validateContainerId(containerId);
        validator.validateSignatureProfile(createSmartIdSigningRequest.getSignatureProfile());

        List<String> roles = createSmartIdSigningRequest.getRoles();
        validator.validateRoles(roles);
        String signatureProfile = createSmartIdSigningRequest.getSignatureProfile();
        SignatureProductionPlace signatureProductionPlace = createSmartIdSigningRequest.getSignatureProductionPlace();

        SignatureParameters signatureParameters = RequestTransformer.transformSignatureParameters(signatureProfile, signatureProductionPlace, roles);
        SmartIdInformation smartIdInformation = getSmartIdInformation(createSmartIdSigningRequest);
        validator.validateSmartIdInformationForSigning(smartIdInformation);

        SigningChallenge signingChallenge = signingService.startSmartIdSigning(containerId, smartIdInformation, signatureParameters);

        CreateHashcodeContainerSmartIdSigningResponse response = new CreateHashcodeContainerSmartIdSigningResponse();
        response.setChallengeId(signingChallenge.getChallengeId());
        response.setGeneratedSignatureId(signingChallenge.getGeneratedSignatureId());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_SMART_ID_SIGNING_STATUS)
    @GetMapping(value = "/hashcodecontainers/{containerId}/smartidsigning/{signatureId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetHashcodeContainerSmartIdSigningStatusResponse getSmartIdSigningStatus(
            @PathVariable(value = "containerId") String containerId,
            @PathVariable(value = "signatureId") String signatureId) {
        validator.validateContainerId(containerId);
        validator.validateSignatureId(signatureId);
        String status = signingService.processSmartIdStatus(containerId, signatureId);

        GetHashcodeContainerSmartIdSigningStatusResponse response = new GetHashcodeContainerSmartIdSigningStatusResponse();
        response.setSidStatus(status);
        return response;
    }

    private SmartIdInformation getSmartIdInformation(CreateHashcodeContainerSmartIdSigningRequest request) {
        return RequestTransformer.transformSmartIdInformation(request.getDocumentNumber(),
                null, request.getMessageToDisplay(), null);
    }

    private SmartIdInformation getSmartIdInformation(CreateHashcodeContainerSmartIdCertificateChoiceRequest request) {
        return RequestTransformer.transformSmartIdInformation(null, request.getCountry(),
                null, request.getPersonIdentifier());
    }

}
