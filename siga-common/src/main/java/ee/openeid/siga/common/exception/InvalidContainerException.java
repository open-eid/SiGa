package ee.openeid.siga.common.exception;

public class InvalidContainerException extends SigaApiException {

    public InvalidContainerException(String message) {
        super("INVALID_CONTAINER_EXCEPTION", message);
    }
}

