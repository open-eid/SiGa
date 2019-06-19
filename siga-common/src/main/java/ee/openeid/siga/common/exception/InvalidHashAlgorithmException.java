package ee.openeid.siga.common.exception;

public class InvalidHashAlgorithmException extends SigaApiException {

    public InvalidHashAlgorithmException(String message) {
        super("INVALID_HASH_ALGORITHM_EXCEPTION", message);
    }
}

