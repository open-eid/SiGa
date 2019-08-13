package ee.openeid.siga;

import ee.openeid.siga.common.SigningChallenge;
import ee.openeid.siga.common.SmartIdInformation;
import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.CreateContainerSmartIdSigningRequest;
import ee.openeid.siga.webapp.json.CreateContainerSmartIdSigningResponse;
import ee.openeid.siga.webapp.json.GetContainerSmartIdSigningStatusResponse;
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
public class SmartIdAsicContainerController {

    private AsicContainerSigningService signingService;

    @SigaEventLog(eventName = SigaEventName.SMART_ID_SIGNING_INIT)
    @PostMapping(value = "/containers/{containerId}/smartidsigning", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateContainerSmartIdSigningResponse createContainerSmartIdSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateContainerSmartIdSigningRequest createSmartIdSigningRequest) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureProfile(createSmartIdSigningRequest.getSignatureProfile());

        List<String> roles = createSmartIdSigningRequest.getRoles();
        String signatureProfile = createSmartIdSigningRequest.getSignatureProfile();
        SignatureProductionPlace signatureProductionPlace = createSmartIdSigningRequest.getSignatureProductionPlace();

        SignatureParameters signatureParameters = RequestTransformer.transformSignatureParameters(signatureProfile, signatureProductionPlace, roles);
        SmartIdInformation smartIdInformation = getSmartIdInformation(createSmartIdSigningRequest);
        RequestValidator.validateSmartIdInformation(smartIdInformation);

        SigningChallenge signingChallenge = signingService.startSmartIdSigning(containerId, getSmartIdInformation(createSmartIdSigningRequest), signatureParameters);

        CreateContainerSmartIdSigningResponse response = new CreateContainerSmartIdSigningResponse();
        response.setChallengeId(signingChallenge.getChallengeId());
        response.setGeneratedSignatureId(signingChallenge.getGeneratedSignatureId());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.SMART_ID_SIGNING_STATUS)
    @GetMapping(value = "/containers/{containerId}/smartidsigning/{signatureId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerSmartIdSigningStatusResponse getSmartSigningStatus(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId) {
        RequestValidator.validateContainerId(containerId);
        RequestValidator.validateSignatureId(signatureId);
        SmartIdInformation smartIdInformation = RequestTransformer.transformSmartIdInformation(null, null, null);
        String status = signingService.processSmartIdStatus(containerId, signatureId, smartIdInformation);

        GetContainerSmartIdSigningStatusResponse response = new GetContainerSmartIdSigningStatusResponse();
        response.setSidStatus(status);
        return response;
    }

    private SmartIdInformation getSmartIdInformation(CreateContainerSmartIdSigningRequest containerSmartIdSigningRequest) {
        String country = containerSmartIdSigningRequest.getCountry();
        String messageToDisplay = containerSmartIdSigningRequest.getMessageToDisplay();
        String personIdentifier = containerSmartIdSigningRequest.getPersonIdentifier();
        return RequestTransformer.transformSmartIdInformation(country, messageToDisplay, personIdentifier);

    }

    @Autowired
    public void setSigningService(AsicContainerSigningService signingService) {
        this.signingService = signingService;
    }
}
