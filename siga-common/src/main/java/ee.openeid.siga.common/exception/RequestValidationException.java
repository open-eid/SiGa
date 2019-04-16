package ee.openeid.siga.common.exception;

public class RequestValidationException extends SigaApiException {

    public RequestValidationException(String message) {
        super(ErrorResponseCode.REQUEST_VALIDATION_EXCEPTION.name(), message);
    }
}


