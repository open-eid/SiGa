package ee.openeid.siga.common.exception;

import static ee.openeid.siga.common.exception.ErrorResponseCode.CLIENT_EXCEPTION;

public class ClientException extends SigaApiException {

    public ClientException(String message, Exception cause) {
        super(CLIENT_EXCEPTION, message, cause);
    }

    public ClientException(String message) {
        super(CLIENT_EXCEPTION, message);
    }
}
