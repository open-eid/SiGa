package ee.openeid.siga.exception;

import ee.openeid.siga.MobileIdAsicContainerController;
import ee.openeid.siga.service.signature.container.asic.AsicContainerSigningService;
import ee.openeid.siga.validation.RequestValidator;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(classes = {
        MobileIdAsicContainerController.class,
        GlobalExceptionHandler.class,
        JacksonAutoConfiguration.class
})

@ActiveProfiles({"mobileId", "datafileContainer"})
class MobileIdAsicContainerControllerGetContainerMobileIdSigningStatusResponseExceptionHandlerTest extends ExceptionHandlerTestBase {

    @MockitoBean
    private AsicContainerSigningService signingService;

    @MockitoBean
    private RequestValidator validator;

    @Override
    protected ResultActions performRequestAndReturnResponse(Throwable toThrowInController) throws Exception {
        String containerId = "dummy-container-Id";
        String signatureId = "dummy-signature-Id";

        doAnswer(invocation -> {
            throw toThrowInController;
        }).when(validator).validateContainerId(anyString());

        String url = "/containers/" + containerId + "/mobileidsigning/" + signatureId + "/status";
        return performGet(url);
    }
}
