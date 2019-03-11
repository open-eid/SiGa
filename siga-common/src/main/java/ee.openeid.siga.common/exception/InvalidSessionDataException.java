package ee.openeid.siga.common.exception;

public class InvalidSessionDataException extends RuntimeException implements LoggableException {

    public InvalidSessionDataException(String message) {
        super(message);
    }

}
