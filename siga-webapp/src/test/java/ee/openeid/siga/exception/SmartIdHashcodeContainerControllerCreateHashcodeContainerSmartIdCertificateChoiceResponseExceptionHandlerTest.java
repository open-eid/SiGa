package ee.openeid.siga.exception;

import ee.openeid.siga.SmartIdHashcodeContainerController;
import ee.openeid.siga.service.signature.container.hashcode.HashcodeContainerSigningService;
import ee.openeid.siga.validation.RequestValidator;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(classes = {
        SmartIdHashcodeContainerController.class,
        GlobalExceptionHandler.class,
        JacksonAutoConfiguration.class
})

@ActiveProfiles("smartId")
class SmartIdHashcodeContainerControllerCreateHashcodeContainerSmartIdCertificateChoiceResponseExceptionHandlerTest extends ExceptionHandlerTestBase {

    @MockitoBean
    private HashcodeContainerSigningService signingService;

    @MockitoBean
    private RequestValidator validator;

    @Override
    protected ResultActions performRequestAndReturnResponse(Throwable toThrowInController) throws Exception {
        String containerId = "dummy-container-Id";

        doAnswer(invocation -> {
            throw toThrowInController;
        }).when(validator).validateContainerId(anyString());

        JSONObject request = createStartSmartIdCertificateChoiceRequest();
        String url = "/hashcodecontainers/" + containerId + "/smartidsigning/certificatechoice";
        return performPost(url, request);
    }
}
