package ee.openeid.siga.common.exception;

public class ClientException extends SigaApiException {

    private static final String ERROR_CODE_STRING = "CLIENT_EXCEPTION";

    public ClientException(String message, Exception cause) {
        super(ERROR_CODE_STRING, message, cause);
    }

    public ClientException(String message) {
        super(ERROR_CODE_STRING, message);
    }
}
