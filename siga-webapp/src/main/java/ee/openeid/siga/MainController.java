package ee.openeid.siga;

import ee.openeid.siga.service.signature.ContainerService;
import ee.openeid.siga.validation.RequestValidator;
import ee.openeid.siga.webapp.json.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
public class MainController {

    private ContainerService containerService;

    @RequestMapping(value = "/container/create", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateContainerResponse createContainer(@RequestBody CreateContainerRequest createContainerRequest) {
        RequestValidator.validateCreateContainerRequest(createContainerRequest);
        return containerService.createContainer(createContainerRequest);
    }

    @RequestMapping(value = "/container/upload", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public UploadContainerResponse uploadContainer(@RequestBody UploadContainerRequest uploadContainerRequest) {
        return null;
    }

    @RequestMapping(value = "/container/validate", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public ValidateContainerResponse validateContainer(@RequestBody ValidateContainerRequest validateContainerRequest) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/container/validate", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetContainerValidationResponse getContainerValidation(@PathVariable(value = "sessionId") String sessionId) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/container/signature/prepare", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public PrepareSignatureResponse prepareSignature(@PathVariable(value = "sessionId") String sessionId, @RequestBody PrepareSignatureRequest prepareSignatureRequest) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/container/signature/finalize", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public FinalizeSignatureResponse finalizeSignature(@PathVariable(value = "sessionId") String sessionId, @RequestBody FinalizeSignatureRequest finalizeSignatureRequest) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/container/signature/start/signing/mobileid", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public StartMobileIdSigningResponse startMobileIdSigning(@PathVariable(value = "sessionId") String sessionId, @RequestBody StartMobileIdSigningRequest startMobileIdSigningRequest) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/container/signature/start/signing/mobileid/status", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetMobileSigningStatusResponse getMobileSigningStatus(@PathVariable(value = "sessionId") String sessionId) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/container/signature/list", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public GetSignatureListResponse getSignatureList(@PathVariable(value = "sessionId") String sessionId) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/close", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public CloseSessionResponse closeSession(@PathVariable(value = "sessionId") String sessionId) {
        return null;
    }

    @Autowired
    public void setContainerService(ContainerService containerService) {
        this.containerService = containerService;
    }
}
