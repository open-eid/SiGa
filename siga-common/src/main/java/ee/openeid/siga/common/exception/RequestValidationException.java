package ee.openeid.siga.common.exception;

import static ee.openeid.siga.common.exception.ErrorResponseCode.REQUEST_VALIDATION_EXCEPTION;

public class RequestValidationException extends SigaApiException {

    public RequestValidationException(String message) {
        super(REQUEST_VALIDATION_EXCEPTION, message);
    }
}


