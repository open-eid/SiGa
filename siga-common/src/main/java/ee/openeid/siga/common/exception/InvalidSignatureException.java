package ee.openeid.siga.common.exception;

public class InvalidSignatureException extends SigaApiException {

    public InvalidSignatureException(String message) {
        super("INVALID_SIGNATURE_EXCEPTION", message);
    }
}
