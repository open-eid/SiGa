package ee.openeid.siga.common.exception;

import static ee.openeid.siga.common.exception.ErrorResponseCode.INVALID_LANGUAGE_EXCEPTION;

public class InvalidLanguageException extends SigaApiException {

    public InvalidLanguageException(String message) {
        super(INVALID_LANGUAGE_EXCEPTION, message);
    }
}
