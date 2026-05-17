package ee.openeid.siga.common.session;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.Value;

import java.io.Serializable;
import java.time.LocalDateTime;

import static ee.openeid.siga.common.session.ProcessingStatus.PROCESSING;
import static ee.openeid.siga.common.session.ProcessingStatus.RESULT;

@Data
@Builder
public class SessionStatus implements Serializable {
    private static final long serialVersionUID = 1L;

    private String status;
    private StatusError statusError;
    @Builder.Default
    private ProcessingStatus processingStatus = PROCESSING;
    private int processingCounter;
    @Setter(AccessLevel.PRIVATE)
    @Builder.Default
    private LocalDateTime processingStatusTimestamp = LocalDateTime.now();

    public void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
        this.processingStatusTimestamp = LocalDateTime.now();
        if (processingStatus != RESULT) {
            processingCounter++;
        }
    }

    public void setStatusError(String errorCode, String message) {
        this.statusError = StatusError.builder()
                .errorCode(errorCode)
                .errorMessage(message)
                .build();
    }

    @Value
    @Builder
    public static class StatusError implements Serializable {
        private static final long serialVersionUID = 1L;

        String errorCode;
        String errorMessage;
    }
}
