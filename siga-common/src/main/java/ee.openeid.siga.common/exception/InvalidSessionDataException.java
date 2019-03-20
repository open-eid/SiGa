package ee.openeid.siga.common.exception;

public class InvalidSessionDataException extends SigaApiException {

    public InvalidSessionDataException(String message) {
        super("INVALID_SESSION_DATA_EXCEPTION", message);
    }

}
