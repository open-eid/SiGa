package ee.openeid.siga.exception;

import ee.openeid.siga.common.exception.ErrorResponseCode;
import ee.openeid.siga.common.exception.SigaApiException;
import ee.openeid.siga.webapp.json.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.ws.soap.client.SoapFaultClientException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SigaApiException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ErrorResponse genericSigaApiException(SigaApiException exception) {
        LOGGER.error("Siga API exception - {}", exception);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorCode(exception.getErrorCode());
        errorResponse.setErrorMessage(exception.getMessage());
        return errorResponse;
    }

    @ExceptionHandler(SoapFaultClientException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse soapFaultClientException(Exception exception) {
        LOGGER.error("Internal server error - {}", exception.getLocalizedMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorMessage("Unable to connect to client");
        errorResponse.setErrorCode(ErrorResponseCode.INTERNAL_SERVER_ERROR.name());
        return errorResponse;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse genericException(Exception exception) {
        LOGGER.error("Internal server error - {}", exception.getLocalizedMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorMessage(exception.getMessage());
        errorResponse.setErrorCode(ErrorResponseCode.INTERNAL_SERVER_ERROR.name());
        return errorResponse;
    }

}
