package ee.openeid.siga.common.exception;

public class ResourceNotFoundException extends SigaApiException {

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND_EXCEPTION", message);
    }
}
