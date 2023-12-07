package ee.openeid.siga.exception;

import ee.openeid.siga.common.exception.ErrorResponseCode;
import ee.openeid.siga.common.exception.SigaApiException;
import ee.openeid.siga.webapp.json.ErrorResponse;
import ee.sk.smartid.exception.SmartIdException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SigaApiException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ErrorResponse genericSigaApiException(SigaApiException exception) {
        log.error("Siga API exception - {}", exception);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorCode(exception.getErrorCode());
        errorResponse.setErrorMessage(exception.getMessage());
        return errorResponse;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ErrorResponse httpMessageNotReadableException(Exception exception) {
        log.error("Siga request exception - {}", exception);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorCode(ErrorResponseCode.REQUEST_VALIDATION_EXCEPTION.name());
        errorResponse.setErrorMessage(exception.getMessage());
        return errorResponse;
    }

    @ExceptionHandler(SmartIdException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ErrorResponse smartIdException(Exception exception) {
        log.error("Siga request exception - {}", exception);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorCode(ErrorResponseCode.SMARTID_EXCEPTION.name());
        String smartIdExceptionString = exception.getClass().getSimpleName();
        errorResponse.setErrorMessage(smartIdExceptionString);
        return errorResponse;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(value = HttpStatus.METHOD_NOT_ALLOWED)
    public ErrorResponse httpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException exception) {
        log.error("Not supported http method - {}", exception.getLocalizedMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorMessage(exception.getMessage());
        errorResponse.setErrorCode(ErrorResponseCode.REQUEST_VALIDATION_EXCEPTION.name());
        return errorResponse;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    public ErrorResponse notFoundException(NoResourceFoundException exception) {
        log.error("Not found exception - {}", exception.getLocalizedMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorMessage(exception.getMessage());
        errorResponse.setErrorCode(ErrorResponseCode.RESOURCE_NOT_FOUND_EXCEPTION.name());
        return errorResponse;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse genericException(Exception exception) {
        log.error("Internal server error - {}", exception.getLocalizedMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorMessage("General service error");
        errorResponse.setErrorCode(ErrorResponseCode.INTERNAL_SERVER_ERROR.name());
        return errorResponse;
    }

}
