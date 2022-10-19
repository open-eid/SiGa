package ee.openeid.siga.common.exception;

import static ee.openeid.siga.common.exception.ErrorResponseCode.RESOURCE_NOT_FOUND_EXCEPTION;

public class ResourceNotFoundException extends SigaApiException {

    public ResourceNotFoundException(String message) {
        super(RESOURCE_NOT_FOUND_EXCEPTION, message);
    }
}
