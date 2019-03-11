package ee.openeid.siga.common.exception;

public class ResourceNotFoundException extends RuntimeException implements LoggableException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
