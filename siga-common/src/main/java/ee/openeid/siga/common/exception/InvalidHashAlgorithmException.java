package ee.openeid.siga.common.exception;

import static ee.openeid.siga.common.exception.ErrorResponseCode.INVALID_HASH_ALGORITHM_EXCEPTION;

public class InvalidHashAlgorithmException extends SigaApiException {

    public InvalidHashAlgorithmException(String message) {
        super(INVALID_HASH_ALGORITHM_EXCEPTION, message);
    }
}
