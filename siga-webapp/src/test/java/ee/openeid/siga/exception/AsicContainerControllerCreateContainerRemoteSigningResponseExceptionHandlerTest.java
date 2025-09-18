package ee.openeid.siga.exception;

import ee.openeid.siga.AsicContainerController;
import ee.openeid.siga.auth.repository.ConnectionRepository;
import ee.openeid.siga.service.signature.container.asic.AsicContainerService;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.service.signature.container.asic.AsicContainerValidationService;
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
        AsicContainerController.class,
        GlobalExceptionHandler.class,
        JacksonAutoConfiguration.class
})

@ActiveProfiles("datafileContainer")
class AsicContainerControllerCreateContainerRemoteSigningResponseExceptionHandlerTest extends ExceptionHandlerTestBase {

    @MockitoBean
    private AsicContainerService containerService;

    @MockitoBean
    private RequestValidator validator;

    @MockitoBean
    private AsicContainerValidationService validationService;

    @MockitoBean
    private AsicContainerSigningService signingService;

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
        String url = "/containers/" + containerId + "/remotesigning";
        return performPost(url, request);
    }
}
