package ee.openeid.siga.common.exception;

import static ee.openeid.siga.common.exception.ErrorResponseCode.SIGNATURE_CREATION_EXCEPTION;

public class SignatureCreationException extends SigaApiException {

    public SignatureCreationException(String message) {
        super(SIGNATURE_CREATION_EXCEPTION, message);
    }

    public SignatureCreationException(String message, Exception cause) {
        super(SIGNATURE_CREATION_EXCEPTION, message, cause);
    }
}
