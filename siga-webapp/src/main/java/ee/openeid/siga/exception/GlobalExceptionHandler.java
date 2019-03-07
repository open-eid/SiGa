package ee.openeid.siga.exception;

import ee.openeid.siga.common.exception.ErrorCode;
import ee.openeid.siga.common.exception.InvalidRequestException;
import ee.openeid.siga.common.exception.ResourceNotFoundException;
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

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ErrorResponse resourceNotFoundException(ResourceNotFoundException exception) {
        LOGGER.debug("Session not found - {}",  exception);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorMessage(exception.getMessage());
        errorResponse.setErrorCode(ErrorCode.SESSION_NOT_FOUND.name());
        return errorResponse;
    }

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidRequestException(InvalidRequestException exception) {
        LOGGER.error("Invalid request - {}",  exception);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorMessage(exception.getMessage());
        errorResponse.setErrorCode(ErrorCode.INVALID_REQUEST.name());
        return errorResponse;
    }

    @ExceptionHandler(SoapFaultClientException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse soapFaultClientException(Exception exception) {
        LOGGER.error("Internal server error - {}", exception.getLocalizedMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorMessage("Unable to connect to client");
        errorResponse.setErrorCode(ErrorCode.INTERNAL_SERVER_ERROR.name());
        return errorResponse;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse genericException(Exception exception) {
        LOGGER.error("Internal server error - {}", exception.getLocalizedMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorMessage(exception.getMessage());
        errorResponse.setErrorCode(ErrorCode.INTERNAL_SERVER_ERROR.name());
        return errorResponse;
    }

}
