package ee.openeid.siga.common.exception;

public class InvalidRequestException extends RuntimeException implements LoggableException {

    public InvalidRequestException(String message) {
        super(message);
    }
}


