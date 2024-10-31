package ee.openeid.siga;

import ee.openeid.siga.common.event.SigaEventLog;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.model.SigningChallenge;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningRequest;
import ee.openeid.siga.webapp.json.CreateHashcodeContainerMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.GetHashcodeContainerMobileIdSigningStatusResponse;
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
@Profile("mobileId")
@RequiredArgsConstructor
public class MobileIdHashcodeContainerController {
    private final HashcodeContainerSigningService signingService;
    private final RequestValidator validator;

    @SigaEventLog(eventName = SigaEventName.HC_MOBILE_ID_SIGNING_INIT)
    @PostMapping(value = "/hashcodecontainers/{containerId}/mobileidsigning", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreateHashcodeContainerMobileIdSigningResponse prepareMobileIdSignatureSigning(@PathVariable(value = "containerId") String containerId, @RequestBody CreateHashcodeContainerMobileIdSigningRequest createMobileIdSigningRequest) {
        validator.validateContainerId(containerId);
        validator.validateSignatureProfileForHashcodeRequest(createMobileIdSigningRequest.getSignatureProfile());

        List<String> roles = createMobileIdSigningRequest.getRoles();
        validator.validateRoles(roles);
        String signatureProfile = createMobileIdSigningRequest.getSignatureProfile();
        SignatureProductionPlace signatureProductionPlace = createMobileIdSigningRequest.getSignatureProductionPlace();

        MobileIdInformation mobileIdInformation = getMobileIdInformation(createMobileIdSigningRequest);

        SignatureParameters signatureParameters = RequestTransformer.transformSignatureParameters(signatureProfile, signatureProductionPlace, roles);
        validator.validateMobileIdInformation(mobileIdInformation);

        SigningChallenge challenge = signingService.startMobileIdSigning(containerId, mobileIdInformation, signatureParameters);

        CreateHashcodeContainerMobileIdSigningResponse response = new CreateHashcodeContainerMobileIdSigningResponse();
        response.setChallengeId(challenge.getChallengeId());
        response.setGeneratedSignatureId(challenge.getGeneratedSignatureId());
        return response;
    }

    @SigaEventLog(eventName = SigaEventName.HC_MOBILE_ID_SIGNING_STATUS)
    @GetMapping(value = "/hashcodecontainers/{containerId}/mobileidsigning/{signatureId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public GetHashcodeContainerMobileIdSigningStatusResponse getMobileSigningStatus(@PathVariable(value = "containerId") String containerId, @PathVariable(value = "signatureId") String signatureId) {
        validator.validateContainerId(containerId);
        validator.validateSignatureId(signatureId);
        String status = signingService.getMobileIdSignatureStatus(containerId, signatureId);

        GetHashcodeContainerMobileIdSigningStatusResponse response = new GetHashcodeContainerMobileIdSigningStatusResponse();
        response.setMidStatus(status);
        return response;
    }

    private MobileIdInformation getMobileIdInformation(CreateHashcodeContainerMobileIdSigningRequest createMobileIdSigningRequest) {
        String language = createMobileIdSigningRequest.getLanguage();
        String messageToDisplay = createMobileIdSigningRequest.getMessageToDisplay();
        String phoneNo = createMobileIdSigningRequest.getPhoneNo();
        String personIdentifier = createMobileIdSigningRequest.getPersonIdentifier();
        return RequestTransformer.transformMobileIdInformation(language, messageToDisplay, personIdentifier, phoneNo);
    }
}
