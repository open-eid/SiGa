package ee.openeid.siga.common.exception;

public class RequestValidationException extends SigaApiException {

    public RequestValidationException(String message) {
        super("REQUEST_VALIDATION_EXCEPTION", message);
    }
}


