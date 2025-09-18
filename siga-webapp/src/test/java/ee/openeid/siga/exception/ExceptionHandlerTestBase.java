package ee.openeid.siga.exception;

import ee.openeid.siga.BaseTest;
import ee.openeid.siga.common.exception.ClientException;
import ee.openeid.siga.common.exception.DuplicateDataFileException;
import ee.openeid.siga.common.exception.ErrorResponseCode;
import ee.openeid.siga.common.exception.InvalidCertificateException;
import ee.openeid.siga.common.exception.InvalidContainerException;
import ee.openeid.siga.common.exception.InvalidHashAlgorithmException;
import ee.openeid.siga.common.exception.InvalidLanguageException;
import ee.openeid.siga.common.exception.InvalidSessionDataException;
import ee.openeid.siga.common.exception.InvalidSignatureException;
import ee.openeid.siga.common.exception.MobileIdApiException;
import ee.openeid.siga.common.exception.RequestValidationException;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
import ee.openeid.siga.common.exception.SiVaHttpErrorException;
import ee.openeid.siga.common.exception.SiVaServiceException;
import ee.openeid.siga.common.exception.SiVaTlsHandshakeException;
import ee.openeid.siga.common.exception.SigaApiException;
import ee.openeid.siga.common.exception.SignatureCreationException;
import ee.openeid.siga.common.exception.SignatureExistsException;
import ee.openeid.siga.common.exception.SmartIdApiException;
import ee.openeid.siga.common.exception.TechnicalException;
import ee.sk.smartid.exception.SessionNotFoundException;
import ee.sk.smartid.exception.SmartIdException;
import ee.sk.smartid.exception.UnprocessableSmartIdResponseException;
import ee.sk.smartid.exception.permanent.ServerMaintenanceException;
import ee.sk.smartid.exception.permanent.SmartIdClientException;
import ee.sk.smartid.exception.useraccount.CertificateLevelMismatchException;
import ee.sk.smartid.exception.useraccount.DocumentUnusableException;
import ee.sk.smartid.exception.useraccount.NoSuitableAccountOfRequestedTypeFoundException;
import ee.sk.smartid.exception.useraccount.PersonShouldViewSmartIdPortalException;
import ee.sk.smartid.exception.useraccount.RequiredInteractionNotSupportedByAppException;
import ee.sk.smartid.exception.useraccount.UserAccountNotFoundException;
import ee.sk.smartid.exception.useraction.SessionTimeoutException;
import ee.sk.smartid.exception.useraction.UserRefusedCertChoiceException;
import ee.sk.smartid.exception.useraction.UserRefusedConfirmationMessageException;
import ee.sk.smartid.exception.useraction.UserRefusedConfirmationMessageWithVerificationChoiceException;
import ee.sk.smartid.exception.useraction.UserRefusedDisplayTextAndPinException;
import ee.sk.smartid.exception.useraction.UserRefusedException;
import ee.sk.smartid.exception.useraction.UserRefusedVerificationChoiceException;
import ee.sk.smartid.exception.useraction.UserSelectedWrongVerificationCodeException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnableWebMvc
@AutoConfigureMockMvc(addFilters = false)
abstract class ExceptionHandlerTestBase extends BaseTest {

    @Autowired
    protected MockMvc mockMvc;

    protected abstract ResultActions performRequestAndReturnResponse(Throwable toThrowInController) throws Exception;

    @ParameterizedTest
    @MethodSource("sigaApiExceptions")
    void request_WhenSigaApiExceptionIsThrown_ReturnsBadRequestAndLogsError(SigaApiException toThrow) throws Exception {
        testException(
                toThrow,
                HttpStatus.BAD_REQUEST.value(),
                toThrow.getErrorCode(),
                toThrow.getMessage()
        );
        assertErrorIsLoggedOnce("Siga API exception - " + toThrow.getMessage());
    }

    @Test
    void request_WhenHttpMessageNotReadableException_BadRequestAndLogsError() throws Exception {
        HttpInputMessage mockInputMessage = getHttpInputMessage();
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException(
                        "Invalid JSON request",
                        mockInputMessage
                );

        testException(
                ex,
                HttpStatus.BAD_REQUEST.value(),
                ErrorResponseCode.REQUEST_VALIDATION_EXCEPTION.name(),
                ex.getMessage()
        );
        assertErrorIsLoggedOnce("Siga request exception - " + ex.getMessage());
    }

    @ParameterizedTest
    @MethodSource("smartIdExceptions")
    void request_WhenSmartIdExceptionIsThrown_ReturnsBadRequestAndLogsError(SmartIdException toThrow) throws Exception {
        testException(
                toThrow,
                HttpStatus.BAD_REQUEST,
                ErrorResponseCode.SMARTID_EXCEPTION,
                toThrow.getClass().getSimpleName()
        );
        assertErrorIsLoggedOnce("Siga request exception - " + toThrow.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UNSUPPORTED"})
    void request_WhenHttpRequestMethodNotSupportedExceptionIsThrown_ReturnsMethodNotAllowedAndLogsError(String method) throws Exception {
        HttpRequestMethodNotSupportedException toThrow = new HttpRequestMethodNotSupportedException(
                method,
                Collections.singletonList("SUPPORTED")
        );

        testException(
                toThrow,
                HttpStatus.METHOD_NOT_ALLOWED,
                ErrorResponseCode.REQUEST_VALIDATION_EXCEPTION,
                toThrow.getMessage()
        );
        assertErrorIsLoggedOnce("Not supported http method - " + toThrow.getMessage());
    }

    @Test
    void request_WhenNoResourceFoundException_NotFoundAndLogsError() throws Exception {
        HttpMethod httpMethod = HttpMethod.GET;
        String resourcePath = "/test/resource";
        NoResourceFoundException ex = new NoResourceFoundException(httpMethod, resourcePath);

        testException(
                ex,
                HttpStatus.NOT_FOUND,
                ErrorResponseCode.RESOURCE_NOT_FOUND_EXCEPTION,
                ex.getMessage()
        );
        assertErrorIsLoggedOnce("Not found exception - " + ex.getMessage());
    }

    @Test
    void request_WhenSiVaServiceException_InternalServerErrorAndLogsError() throws Exception {
        Throwable cause = new RuntimeException("Something happened");
        SiVaServiceException ex = new SiVaServiceException(cause.getMessage(), cause);

        testException(
                ex,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorResponseCode.INTERNAL_SERVER_ERROR,
                "Failed to execute request to SiVa service"
        );
        assertErrorIsLoggedOnce("Siva service error - " + ex.getMessage());
    }


    @Test
    void request_WhenSiVaHttpErrorException_InternalServerErrorAndLogsError() throws Exception {
        Throwable cause = new RuntimeException("Could not validate request");
        SiVaHttpErrorException ex = new SiVaHttpErrorException(cause);

        testException(
                ex,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorResponseCode.INTERNAL_SERVER_ERROR,
                ex.getMessage()
        );
        assertErrorIsLoggedOnce("Siva response error - " + ex.getMessage());
    }


    @Test
    void request_WhenSiVaTlsHandshakeException_InternalServerErrorAndLogsError() throws Exception {
        Throwable cause = new RuntimeException("TLS handshake failed");
        SiVaTlsHandshakeException ex = new SiVaTlsHandshakeException(cause.getMessage(), cause);

        testException(
                ex,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorResponseCode.INTERNAL_SERVER_ERROR,
                "General service error"
        );
        assertErrorIsLoggedOnce("TLS connection to Siva failed - " + ex.getMessage());
    }

    @ParameterizedTest
    @MethodSource("unhandledExceptions")
    void request_WhenUnhandledExceptionIsThrown_ReturnsInternalServerErrorAndLogsError(RuntimeException toThrow) throws Exception {
        testException(
                toThrow,
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorResponseCode.INTERNAL_SERVER_ERROR,
                "General service error"
        );
        assertErrorIsLoggedOnce("Internal server error - " + toThrow.getMessage());
    }

    protected ResultActions performGet(String urlTemplate, Object... uriVars) throws Exception {
        return mockMvc.perform(
                get(urlTemplate, uriVars)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
        );
    }

    protected ResultActions performPost(String urlTemplate, JSONObject body) throws Exception {
        return mockMvc.perform(post(urlTemplate)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    protected ResultActions performPut(String urlTemplate, JSONObject body) throws Exception {
        return mockMvc.perform(put(urlTemplate)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    protected ResultActions performDelete(String urlTemplate, Object... uriVars) throws Exception {
        return mockMvc.perform(
                delete(urlTemplate, uriVars)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
        );
    }

    protected void assertErrorResponse(ResultActions resultActions, int expectedStatus, String expectedErrorCode, String expectedMessage) throws Exception {
        resultActions
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.errorCode").value(expectedErrorCode))
                .andExpect(jsonPath("$.errorMessage").value(expectedMessage));
    }

    protected void testException(Throwable exception, HttpStatus expectedStatus, ErrorResponseCode expectedErrorCode, String expectedMessage) throws Exception {
        testException(exception, expectedStatus.value(), expectedErrorCode.name(), expectedMessage);
    }

    protected void testException(Throwable exception, int expectedStatus, String expectedErrorCode, String expectedMessage) throws Exception {
        ResultActions result = performRequestAndReturnResponse(exception);
        assertErrorResponse(result, expectedStatus, expectedErrorCode, expectedMessage);
    }

    private static HttpInputMessage getHttpInputMessage() {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream("{}".getBytes());
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        };
    }

    static Stream<RuntimeException> unhandledExceptions() {
        return Stream.of(
                new RuntimeException("Generic runtime exception"),
                new TechnicalException("Technical exception"),
                new IllegalArgumentException("Illegal argument exception"),
                new IllegalStateException("Illegal state exception")
        );
    }

    static Stream<SmartIdException> smartIdExceptions() {
        return Stream.of(
                new CertificateLevelMismatchException(),
                new DocumentUnusableException(),
                new NoSuitableAccountOfRequestedTypeFoundException(),
                new PersonShouldViewSmartIdPortalException(),
                new RequiredInteractionNotSupportedByAppException(),
                new ServerMaintenanceException(),
                new SessionNotFoundException(),
                new SessionTimeoutException(),
                new SmartIdClientException("Smart-ID client exception"),
                new UnprocessableSmartIdResponseException("Unprocessable Smart-ID exception"),
                new UserAccountNotFoundException(),
                new UserRefusedCertChoiceException(),
                new UserRefusedConfirmationMessageException(),
                new UserRefusedConfirmationMessageWithVerificationChoiceException(),
                new UserRefusedDisplayTextAndPinException(),
                new UserRefusedException(),
                new UserRefusedVerificationChoiceException(),
                new UserSelectedWrongVerificationCodeException()
        );
    }

    static Stream<SigaApiException> sigaApiExceptions() {
        return Stream.of(
                new SigaApiException(ErrorResponseCode.AUTHORIZATION_ERROR, "Generic API exception"),
                new ClientException("Client exception"),
                new DuplicateDataFileException("Duplicate data file exception"),
                new InvalidCertificateException("Invalid certificate exception"),
                new InvalidContainerException("Invalid container exception"),
                new InvalidHashAlgorithmException("Invalid hash algorithm exception"),
                new InvalidLanguageException("Invalid language exception"),
                new InvalidSessionDataException("Invalid session data exception"),
                new InvalidSignatureException("Invalid signature exception"),
                new MobileIdApiException("Mobile-ID API exception"),
                new RequestValidationException("Request validation exception"),
                new ResourceNotFoundException("Resource not found exception"),
                new SignatureCreationException("Signature creation exception"),
                new SignatureExistsException("Signature exists exception"),
                new SmartIdApiException("Smart-ID API exception")
        );
    }
}
