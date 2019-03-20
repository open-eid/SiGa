package ee.openeid.siga.common.exception;

public class InvalidRequestException extends SigaApiException {

    public InvalidRequestException(String message) {
        super("INVALID_REQUEST_EXCEPTION", message);
    }
}


