package ee.openeid.siga.common.exception;

public class SignatureCreationException extends SigaApiException {

    public SignatureCreationException(String message) {
        super("SIGNATURE_CREATION_EXCEPTION", message);
    }
}
