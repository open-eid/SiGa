package ee.openeid.siga;

import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.model.CertificateStatus;
import ee.openeid.siga.common.model.SigningChallenge;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.CreateContainerSmartIdCertificateChoiceRequest;
import ee.openeid.siga.webapp.json.CreateContainerSmartIdCertificateChoiceResponse;
import ee.openeid.siga.webapp.json.CreateContainerSmartIdSigningRequest;
import ee.openeid.siga.webapp.json.CreateContainerSmartIdSigningResponse;


import ee.openeid.siga.webapp.json.GetContainerSmartIdCertificateChoiceStatusResponse;
import ee.openeid.siga.webapp.json.GetContainerSmartIdSigningStatusResponse;
import ee.openeid.siga.webapp.json.SignatureProductionPlace;
import lombok.RequiredArgsConstructor;
import org.digidoc4j.SignatureParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Profile("smartId & datafileContainer")
@RequiredArgsConstructor
public class SmartIdAsicContainerController {
    private final AsicContainerSigningService signingService;
    private final RequestValidator validator;

    @SigaEventLog(eventName = SigaEventName.SMART_ID_CERTIFICATE_CHOICE_INIT)
    @PostMapping(value = "/containers/{containerId}/smartidsigning/certificatechoice", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateContainerSmartIdCertificateChoiceResponse createContainerSmartIdCertificateChoice(
            @PathVariable(value = "containerId") String containerId,
            @RequestBody CreateContainerSmartIdCertificateChoiceRequest certificateChoiceRequest) {

        SmartIdInformation smartIdInformation = getSmartIdInformation(certificateChoiceRequest);
        validator.validateContainerId(containerId);
        validator.validateSmartIdInformationForCertChoice(smartIdInformation);

        String certificateId = signingService.initSmartIdCertificateChoice(containerId, smartIdInformation);
        CreateContainerSmartIdCertificateChoiceResponse response = new CreateContainerSmartIdCertificateChoiceResponse();
        response.setGeneratedCertificateId(certificateId);
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.SMART_ID_CERTIFICATE_CHOICE_STATUS)
    @GetMapping(value = "/containers/{containerId}/smartidsigning/certificatechoice/{certificateId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerSmartIdCertificateChoiceStatusResponse getSmartIdCertificateChoiceStatus(
            @PathVariable(value = "containerId") String containerId,
            @PathVariable(value = "certificateId") String certificateId) {

        validator.validateContainerId(containerId);
        validator.validateCertificateId(certificateId);

        CertificateStatus status = signingService.getSmartIdCertificateStatus(containerId, certificateId);
        GetContainerSmartIdCertificateChoiceStatusResponse response = new GetContainerSmartIdCertificateChoiceStatusResponse();
        response.setSidStatus(status.getStatus());
        response.setDocumentNumber(status.getDocumentNumber());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.SMART_ID_SIGNING_INIT)
    @PostMapping(value = "/containers/{containerId}/smartidsigning", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateContainerSmartIdSigningResponse createContainerSmartIdSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateContainerSmartIdSigningRequest createSmartIdSigningRequest) {
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

        CreateContainerSmartIdSigningResponse response = new CreateContainerSmartIdSigningResponse();
        response.setChallengeId(signingChallenge.getChallengeId());
        response.setGeneratedSignatureId(signingChallenge.getGeneratedSignatureId());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.SMART_ID_SIGNING_STATUS)
    @GetMapping(value = "/containers/{containerId}/smartidsigning/{signatureId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerSmartIdSigningStatusResponse getSmartIdSigningStatus(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId) {
        validator.validateContainerId(containerId);
        validator.validateSignatureId(signatureId);

        String status = signingService.getSmartIdSignatureStatus(containerId, signatureId);

        GetContainerSmartIdSigningStatusResponse response = new GetContainerSmartIdSigningStatusResponse();
        response.setSidStatus(status);
        return response;
    }

    private SmartIdInformation getSmartIdInformation(CreateContainerSmartIdSigningRequest request) {
        return RequestTransformer.transformSmartIdInformation(request.getDocumentNumber(),
                null, request.getMessageToDisplay(), null);
    }

    private SmartIdInformation getSmartIdInformation(CreateContainerSmartIdCertificateChoiceRequest request) {
        return RequestTransformer.transformSmartIdInformation(null, request.getCountry(),
                null, request.getPersonIdentifier());
    }
}
