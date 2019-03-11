package ee.openeid.siga.common.exception;

public class ClientException extends RuntimeException implements LoggableException {

    public ClientException(String message) {
        super(message);
    }
}
