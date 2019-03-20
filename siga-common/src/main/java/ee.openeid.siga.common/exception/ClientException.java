package ee.openeid.siga.common.exception;

public class ClientException extends SigaApiException {

    public ClientException(String message) {
        super("CLIENT_EXCEPTION", message);
    }
}
