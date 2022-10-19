package ee.openeid.siga.common.exception;

import static ee.openeid.siga.common.exception.ErrorResponseCode.INVALID_CONTAINER_EXCEPTION;

public class InvalidContainerException extends SigaApiException {

    public InvalidContainerException(String message) {
        super(INVALID_CONTAINER_EXCEPTION, message);
    }
}
