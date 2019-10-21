package ee.openeid.siga;


import ee.openeid.siga.common.SigningChallenge;
import ee.openeid.siga.common.SmartIdInformation;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerSmartIdSigningRequest;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerSmartIdSigningResponse;
import ee.openeid.siga.webapp.json.GetHashcodeContainerSmartIdSigningStatusResponse;
import ee.openeid.siga.webapp.json.SignatureProductionPlace;
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
@Profile("smartId")
public class SmartIdHashcodeContainerController {

    private HashcodeContainerSigningService signingService;

    @SigaEventLog(eventName = SigaEventName.HC_SMART_ID_SIGNING_INIT)
    @PostMapping(value = "/hashcodecontainers/{containerId}/smartidsigning", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateHashcodeContainerSmartIdSigningResponse createHashcodeContainerSmartIdSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashcodeContainerSmartIdSigningRequest createSmartIdSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureProfile(createSmartIdSigningRequest.getSignatureProfile());

        List<String> roles = createSmartIdSigningRequest.getRoles();
        String signatureProfile = createSmartIdSigningRequest.getSignatureProfile();
        SignatureProductionPlace signatureProductionPlace = createSmartIdSigningRequest.getSignatureProductionPlace();

        SignatureParameters signatureParameters = RequestTransformer.transformSignatureParameters(signatureProfile, signatureProductionPlace, roles);
        SmartIdInformation smartIdInformation = getSmartIdInformation(createSmartIdSigningRequest);
        RequestValidator.validateSmartIdInformation(smartIdInformation);

        SigningChallenge signingChallenge = signingService.startSmartIdSigning(containerId, getSmartIdInformation(createSmartIdSigningRequest), signatureParameters);

        CreateHashcodeContainerSmartIdSigningResponse response = new CreateHashcodeContainerSmartIdSigningResponse();
        response.setChallengeId(signingChallenge.getChallengeId());
        response.setGeneratedSignatureId(signingChallenge.getGeneratedSignatureId());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_SMART_ID_SIGNING_STATUS)
    @GetMapping(value = "/hashcodecontainers/{containerId}/smartidsigning/{signatureId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetHashcodeContainerSmartIdSigningStatusResponse getSmartIdSigningStatus(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureId(signatureId);
        SmartIdInformation smartIdInformation = RequestTransformer.transformSmartIdInformation(null, null, null);
        String status = signingService.processSmartIdStatus(containerId, signatureId, smartIdInformation);

        GetHashcodeContainerSmartIdSigningStatusResponse response = new GetHashcodeContainerSmartIdSigningStatusResponse();
        response.setSidStatus(status);
        return response;
    }


    private SmartIdInformation getSmartIdInformation(CreateHashcodeContainerSmartIdSigningRequest containerSmartIdSigningRequest) {
        String country = containerSmartIdSigningRequest.getCountry();
        String messageToDisplay = containerSmartIdSigningRequest.getMessageToDisplay();
        String personIdentifier = containerSmartIdSigningRequest.getPersonIdentifier();
        return RequestTransformer.transformSmartIdInformation(country, messageToDisplay, personIdentifier);

    }

    @Autowired
    public void setSigningService(HashcodeContainerSigningService signingService) {
        this.signingService = signingService;
    }
}
