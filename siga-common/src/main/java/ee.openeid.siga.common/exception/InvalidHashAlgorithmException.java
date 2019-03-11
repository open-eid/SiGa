package ee.openeid.siga.common.exception;

public class InvalidHashAlgorithmException extends RuntimeException implements LoggableException {

    public InvalidHashAlgorithmException(String message) {
        super(message);
    }
}

