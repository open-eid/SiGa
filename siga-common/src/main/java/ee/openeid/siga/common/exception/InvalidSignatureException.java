package ee.openeid.siga.common.exception;

import static ee.openeid.siga.common.exception.ErrorResponseCode.INVALID_SIGNATURE_EXCEPTION;

public class InvalidSignatureException extends SigaApiException {

    public InvalidSignatureException(String message) {
        super(INVALID_SIGNATURE_EXCEPTION, message);
    }
}
