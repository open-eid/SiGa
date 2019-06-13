package ee.openeid.siga.common.exception;

public class InvalidLanguageException extends SigaApiException {

    public InvalidLanguageException(String message) {
        super("INVALID_LANGUAGE_EXCEPTION", message);
    }
}
