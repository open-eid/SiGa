package ee.openeid.siga.common.exception;

public class SignatureExistsException extends RuntimeException implements LoggableException {

    public SignatureExistsException(String message) {
        super(message);
    }
}


