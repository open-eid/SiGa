package ee.openeid.siga.common.exception;

public class SignatureExistsException extends SigaApiException {

    public SignatureExistsException(String message) {
        super("SIGNATURE_EXISTS_EXCEPTION", message);
    }
}


