package ee.openeid.siga.common.exception;

import static ee.openeid.siga.common.exception.ErrorResponseCode.SIGNATURE_EXISTS_EXCEPTION;

public class SignatureExistsException extends SigaApiException {

    public SignatureExistsException(String message) {
        super(SIGNATURE_EXISTS_EXCEPTION, message);
    }
}


