package ee.openeid.siga.common.exception;

import lombok.Getter;

public abstract class SigaApiException extends RuntimeException {

    @Getter
    private final String errorCode;

    public SigaApiException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
    }
}
