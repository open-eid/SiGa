package ee.ria.siga;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
public class MainController {

    @RequestMapping(value = "/container/create", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public ee.openeid.siga.webapp.json.CreateContainerResponse createContainer(@RequestBody ee.openeid.siga.webapp.json.CreateContainerRequest createContainerRequest) {
        return null;
    }

    @RequestMapping(value = "/container/upload", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public ee.openeid.siga.webapp.json.UploadContainerResponse uploadContainer(@RequestBody ee.openeid.siga.webapp.json.UploadContainerRequest uploadContainerRequest) {
        return null;
    }

    @RequestMapping(value = "/container/validate", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public ee.openeid.siga.webapp.json.ValidateContainerResponse validateContainer(@RequestBody ee.openeid.siga.webapp.json.ValidateContainerRequest validateContainerRequest) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/container/validate", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public ee.openeid.siga.webapp.json.GetContainerValidationResponse getContainerValidation(@PathVariable(value = "sessionId") String sessionId) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/container/signature/prepare", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public ee.openeid.siga.webapp.json.PrepareSignatureResponse prepareSignature(@PathVariable(value = "sessionId") String sessionId, @RequestBody ee.openeid.siga.webapp.json.PrepareSignatureRequest prepareSignatureRequest) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/container/signature/finalize", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public ee.openeid.siga.webapp.json.FinalizeSignatureResponse finalizeSignature(@PathVariable(value = "sessionId") String sessionId, @RequestBody ee.openeid.siga.webapp.json.FinalizeSignatureRequest finalizeSignatureRequest) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/container/signature/start/signing/mobileid", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public ee.openeid.siga.webapp.json.StartMobileIdSigningResponse startMobileIdSigning(@PathVariable(value = "sessionId") String sessionId, @RequestBody ee.openeid.siga.webapp.json.StartMobileIdSigningRequest startMobileIdSigningRequest) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/container/signature/start/signing/mobileid/status", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public ee.openeid.siga.webapp.json.GetMobileSigningStatusResponse getMobileSigningStatus(@PathVariable(value = "sessionId") String sessionId) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/container/signature/list", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public ee.openeid.siga.webapp.json.GetSignatureListResponse getSignatureList(@PathVariable(value = "sessionId") String sessionId) {
        return null;
    }

    @RequestMapping(value = "/session/{sessionId}/close", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public ee.openeid.siga.webapp.json.CloseSessionResponse closeSession(@PathVariable(value = "sessionId") String sessionId) {
        return null;
    }

}
