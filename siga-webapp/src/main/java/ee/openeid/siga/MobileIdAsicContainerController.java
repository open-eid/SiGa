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
import lombok.RequiredArgsConstructor;
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
@Profile("mobileId & datafileContainer")
@RequiredArgsConstructor
public class MobileIdAsicContainerController {
    private final AsicContainerSigningService signingService;
    private final RequestValidator validator;

    @SigaEventLog(eventName = SigaEventName.MOBILE_ID_SIGNING_INIT)
    @PostMapping(value = "/containers/{containerId}/mobileidsigning", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateContainerMobileIdSigningResponse prepareMobileIdSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateContainerMobileIdSigningRequest createMobileIdSigningRequest) {
        validator.validateContainerId(containerId);
        validator.validateSignatureProfileForDatafileRequest(createMobileIdSigningRequest.getSignatureProfile());

        List<String> roles = createMobileIdSigningRequest.getRoles();
        validator.validateRoles(roles);
        String signatureProfile = createMobileIdSigningRequest.getSignatureProfile();
        SignatureProductionPlace signatureProductionPlace = createMobileIdSigningRequest.getSignatureProductionPlace();

        MobileIdInformation mobileIdInformation = getMobileIdInformation(createMobileIdSigningRequest);
        SignatureParameters signatureParameters = RequestTransformer.transformSignatureParameters(signatureProfile, signatureProductionPlace, roles);
        validator.validateMobileIdInformation(mobileIdInformation);

        SigningChallenge challenge = signingService.startMobileIdSigning(containerId, mobileIdInformation, signatureParameters);

        CreateContainerMobileIdSigningResponse response = new CreateContainerMobileIdSigningResponse();
        response.setChallengeId(challenge.getChallengeId());
        response.setGeneratedSignatureId(challenge.getGeneratedSignatureId());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.MOBILE_ID_SIGNING_STATUS)
    @GetMapping(value = "/containers/{containerId}/mobileidsigning/{signatureId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetContainerMobileIdSigningStatusResponse getMobileSigningStatus(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId) {
        validator.validateContainerId(containerId);
        validator.validateSignatureId(signatureId);
        String status = signingService.getMobileIdSignatureStatus(containerId, signatureId);

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
