package ee.openeid.siga.common.exception;

import lombok.Getter;

public class SigaApiException extends RuntimeException {

    @Getter
    private final String errorCode;

    public SigaApiException(ErrorResponseCode errorCode, String errorMessage, Exception cause) {
        super(errorMessage, cause);
        this.errorCode = errorCode.name();
    }

    public SigaApiException(ErrorResponseCode errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode.name();
    }
}
