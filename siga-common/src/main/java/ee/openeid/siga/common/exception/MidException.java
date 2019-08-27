package ee.openeid.siga.common.exception;

public class MidException extends SigaApiException {

    public MidException(String message, Exception cause) {
        super(ErrorResponseCode.MID_EXCEPTION.name(), message, cause);
    }

    public MidException(String message) {
        super(ErrorResponseCode.MID_EXCEPTION.name(), message);
    }

}
