package ee.openeid.siga.common.exception;

public class TechnicalException extends RuntimeException implements LoggableException {

    public TechnicalException(String message) {
        super(message);
    }
}

