package ee.openeid.siga.client.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessingStatus {

    private Status status;
    private String message;
    private Object apiResponseObject;

    public enum Status {
        START, PROCESSING, CHALLENGE, VALIDATION, RESULT, FINISH, ERROR
    }
}
