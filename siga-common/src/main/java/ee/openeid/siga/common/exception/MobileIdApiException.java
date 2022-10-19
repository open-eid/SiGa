package ee.openeid.siga.common.exception;

import static ee.openeid.siga.common.exception.ErrorResponseCode.MID_EXCEPTION;

public class MobileIdApiException extends SigaApiException {

    public MobileIdApiException(String message, Exception cause) {
        super(MID_EXCEPTION, message, cause);
    }

    public MobileIdApiException(String message) {
        super(MID_EXCEPTION, message);
    }
}
