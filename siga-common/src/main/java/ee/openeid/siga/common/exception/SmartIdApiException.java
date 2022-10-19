package ee.openeid.siga.common.exception;

import static ee.openeid.siga.common.exception.ErrorResponseCode.SMARTID_EXCEPTION;

public class SmartIdApiException extends SigaApiException {

    public SmartIdApiException(String message) {
        super(SMARTID_EXCEPTION, message);
    }
}
