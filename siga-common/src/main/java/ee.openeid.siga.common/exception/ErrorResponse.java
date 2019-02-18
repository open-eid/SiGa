package ee.openeid.siga.common.exception;

import lombok.Data;

@Data
public class ErrorResponse {
    private String errorCode;
    private String errorMessage;
}
