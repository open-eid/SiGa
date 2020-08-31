package ee.openeid.siga;

import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.SigningChallenge;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.CreateContainerMobileIdSigningRequest;
import ee.openeid.siga.webapp.json.CreateContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.GetContainerMobileIdSigningStatusResponse;
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

@Profile("mobileId & datafileContainer")
@RestController
public class MobileIdAsicContainerController {

    private final AsicContainerSigningService signingService;

    @Autowired
    public MobileIdAsicContainerController(AsicContainerSigningService signingService) {
        this.signingService = signingService;
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

    private MobileIdInformation getMobileIdInformation(CreateContainerMobileIdSigningRequest createMobileIdSigningRequest) {
        String language = createMobileIdSigningRequest.getLanguage();
        String messageToDisplay = createMobileIdSigningRequest.getMessageToDisplay();
        String phoneNo = createMobileIdSigningRequest.getPhoneNo();
        String personIdentifier = createMobileIdSigningRequest.getPersonIdentifier();
        return RequestTransformer.transformMobileIdInformation(language, messageToDisplay, personIdentifier, phoneNo);
    }
}
