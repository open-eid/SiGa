package ee.openeid.siga;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import ee.openeid.siga.webapp.json.CloseSessionResponse;
import ee.openeid.siga.webapp.json.CreateContainerRequest;
import ee.openeid.siga.webapp.json.CreateContainerResponse;
import ee.openeid.siga.webapp.json.FinalizeSignatureRequest;
import ee.openeid.siga.webapp.json.FinalizeSignatureResponse;
import ee.openeid.siga.webapp.json.GetContainerValidationResponse;
import ee.openeid.siga.webapp.json.GetMobileSigningStatusResponse;
import ee.openeid.siga.webapp.json.GetSignatureListResponse;
import ee.openeid.siga.webapp.json.PrepareSignatureRequest;
import ee.openeid.siga.webapp.json.PrepareSignatureResponse;
import ee.openeid.siga.webapp.json.StartMobileIdSigningRequest;
import ee.openeid.siga.webapp.json.StartMobileIdSigningResponse;
import ee.openeid.siga.webapp.json.UploadContainerRequest;
import ee.openeid.siga.webapp.json.UploadContainerResponse;
import ee.openeid.siga.webapp.json.ValidateContainerRequest;
import ee.openeid.siga.webapp.json.ValidateContainerResponse;

@RestController
public class MainController {

    @RequestMapping(value = "/container/create", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public CreateContainerResponse createContainer(@RequestBody CreateContainerRequest createContainerRequest) {
        return null;
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

}
