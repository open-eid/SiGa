package ee.openeid.siga.exception;

import ee.openeid.siga.HashcodeContainerController;
import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerValidationService;
import ee.openeid.siga.validation.RequestValidator;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Base64;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(classes = {
        HashcodeContainerController.class,
        GlobalExceptionHandler.class,
        JacksonAutoConfiguration.class
})

@ActiveProfiles("datafileContainer")
class HashcodeContainerControllerCreateHashcodeContainerRemoteSigningResponseExceptionHandlerTest extends ExceptionHandlerTestBase {

    @MockitoBean
    private HashcodeContainerService containerService;

    @MockitoBean
    private RequestValidator validator;

    @MockitoBean
    private HashcodeContainerValidationService validationService;

    @MockitoBean
    private HashcodeContainerSigningService signingService;

    @MockitoBean
    private ConnectionRepository connectionRepository;

    @Override
    protected ResultActions performRequestAndReturnResponse(Throwable toThrowInController) throws Exception {
        String containerId = "dummy-container-id";

        doAnswer(invocation -> {
            throw toThrowInController;
        }).when(validator).validateContainerId(anyString());

        String signingCertificate = Base64.getEncoder().encodeToString(pkcs12Esteid2018SignatureToken.getCertificate().getEncoded());
        JSONObject request = createStartRemoteSigningRequest(signingCertificate);
        String url = "/hashcodecontainers/" + containerId + "/remotesigning";
        return performPost(url, request);
    }
}
