package ee.openeid.siga.common.exception;

import static ee.openeid.siga.common.exception.ErrorResponseCode.INVALID_SESSION_DATA_EXCEPTION;

public class InvalidSessionDataException extends SigaApiException {

    public InvalidSessionDataException(String message) {
        super(INVALID_SESSION_DATA_EXCEPTION, message);
    }
}
