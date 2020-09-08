package ee.openeid.siga.common.exception;

public class SigaSmartIdException extends SigaApiException {

    public SigaSmartIdException(String message) {
        super(ErrorResponseCode.SMARTID_EXCEPTION.name(), message);
    }

}
