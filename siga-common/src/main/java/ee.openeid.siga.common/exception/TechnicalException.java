package ee.openeid.siga.common.exception;

public class TechnicalException extends SigaApiException {

    public TechnicalException(String message) {
        super("TECHNICAL_EXCEPTION", message);
    }
}

