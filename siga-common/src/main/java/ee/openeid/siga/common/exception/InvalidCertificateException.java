package ee.openeid.siga.common.exception;

public class InvalidCertificateException extends SigaApiException {

    public InvalidCertificateException(String message) {
        super("INVALID_CERTIFICATE_EXCEPTION", message);
    }
}
