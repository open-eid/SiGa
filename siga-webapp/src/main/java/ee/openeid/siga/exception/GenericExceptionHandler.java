package ee.openeid.siga.exception;

import ee.openeid.siga.common.exception.ErrorCode;
import ee.openeid.siga.common.exception.InvalidRequestException;
import ee.openeid.siga.webapp.json.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GenericExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(value = HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidRequestException(InvalidRequestException exception) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorMessage(exception.getMessage());
        errorResponse.setErrorCode(ErrorCode.INVALID_REQUEST.name());
        return errorResponse;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse genericException(Exception exception) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setErrorMessage(exception.getMessage());
        errorResponse.setErrorCode(ErrorCode.INTERNAL_SERVER_ERROR.name());
        return errorResponse;
    }

}
