package ee.openeid.siga.common.exception;

import static ee.openeid.siga.common.exception.ErrorResponseCode.INVALID_CERTIFICATE_EXCEPTION;

public class InvalidCertificateException extends SigaApiException {

    public InvalidCertificateException(String message) {
        super(INVALID_CERTIFICATE_EXCEPTION, message);
    }
}
